#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include <mutex>
#include <thread>
#include <cstdint>
#include "llama.h"

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;
static std::mutex llama_mutex;

static bool format_chat_prompt(const std::string & input, std::string & formatted) {
    const std::string system_marker = "[JARVIS_SYSTEM]\n";
    if (input.rfind(system_marker, 0) != 0) {
        formatted = input;
        return false;
    }

    std::vector<std::string> roles;
    std::vector<std::string> contents;
    roles.reserve(10);
    contents.reserve(10);
    size_t cursor = system_marker.size();
    roles.emplace_back("system");

    auto find_next_marker = [&](size_t from, size_t & marker_pos, std::string & role, size_t & content_start) -> bool {
        const std::pair<const char *, const char *> markers[] = {
            {"\n[JARVIS_USER]\n", "user"},
            {"\n[JARVIS_ASSISTANT]\n", "assistant"}
        };
        marker_pos = std::string::npos;
        const char * selected = nullptr;
        for (const auto & marker : markers) {
            const size_t pos = input.find(marker.first, from);
            if (pos != std::string::npos && (marker_pos == std::string::npos || pos < marker_pos)) {
                marker_pos = pos;
                role = marker.second;
                selected = marker.first;
            }
        }
        if (marker_pos == std::string::npos) return false;
        content_start = marker_pos + std::char_traits<char>::length(selected);
        return true;
    };

    while (true) {
        size_t marker_pos = std::string::npos;
        size_t next_content = 0;
        std::string next_role;
        const bool has_next = find_next_marker(cursor, marker_pos, next_role, next_content);
        const size_t end = has_next ? marker_pos : input.size();
        contents.emplace_back(input.substr(cursor, end - cursor));
        if (!has_next) break;
        roles.push_back(next_role);
        cursor = next_content;
    }

    if (roles.size() != contents.size() || contents.empty()) {
        formatted = input;
        return false;
    }

    std::vector<llama_chat_message> messages;
    messages.reserve(roles.size());
    for (size_t i = 0; i < roles.size(); ++i) {
        messages.push_back({roles[i].c_str(), contents[i].c_str()});
    }

    const char * chat_template = llama_model_chat_template(model, nullptr);
    if (chat_template == nullptr) {
        formatted = input;
        return false;
    }
    size_t capacity = std::max<size_t>(2048, input.size() * 2 + 512);
    std::vector<char> buffer(capacity);
    int32_t required = llama_chat_apply_template(
        chat_template, messages.data(), messages.size(), true,
        buffer.data(), static_cast<int32_t>(buffer.size())
    );
    if (required <= 0) {
        formatted = input;
        return false;
    }
    if (static_cast<size_t>(required) >= buffer.size()) {
        buffer.resize(static_cast<size_t>(required) + 1);
        required = llama_chat_apply_template(
            chat_template, messages.data(), messages.size(), true,
            buffer.data(), static_cast<int32_t>(buffer.size())
        );
    }
    if (required <= 0 || static_cast<size_t>(required) >= buffer.size()) {
        formatted = input;
        return false;
    }
    formatted.assign(buffer.data(), static_cast<size_t>(required));
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ai_jarvis_offline_NativeLlama_loadModel(JNIEnv * env, jobject, jstring path, jint contextSize) {
    std::lock_guard<std::mutex> guard(llama_mutex);
    if (path == nullptr || contextSize < 256) return JNI_FALSE;

    const char * path_chars = env->GetStringUTFChars(path, nullptr);
    if (path_chars == nullptr) return JNI_FALSE;
    const std::string model_path(path_chars);
    env->ReleaseStringUTFChars(path, path_chars);

    // Free the previous model before loading another one. Keeping two GGUF
    // weights resident at once can exceed RAM on 4 GB Android phones.
    if (ctx != nullptr) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model * candidate_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (candidate_model == nullptr) return JNI_FALSE;

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(contextSize);
    context_params.n_batch = std::min<uint32_t>(256, static_cast<uint32_t>(contextSize));
    llama_context * candidate_context = llama_init_from_model(candidate_model, context_params);
    if (candidate_context == nullptr) {
        llama_model_free(candidate_model);
        return JNI_FALSE;
    }

    model = candidate_model;
    ctx = candidate_context;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_ai_jarvis_offline_NativeLlama_generate(JNIEnv * env, jobject, jstring input, jint maxTokens) {
    std::lock_guard<std::mutex> guard(llama_mutex);
    if (model == nullptr || ctx == nullptr) return env->NewStringUTF("Load a GGUF model first.");
    if (input == nullptr) return env->NewStringUTF("Prompt is empty.");

    const char * input_chars = env->GetStringUTFChars(input, nullptr);
    if (input_chars == nullptr) return env->NewStringUTF("Could not read the prompt.");
    const std::string raw_prompt(input_chars);
    env->ReleaseStringUTFChars(input, input_chars);

    std::string prompt;
    format_chat_prompt(raw_prompt, prompt);

    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens(prompt.size() + 32);
    int n = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                           tokens.data(), static_cast<int>(tokens.size()), true, true);
    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(vocab, prompt.c_str(), static_cast<int>(prompt.size()),
                           tokens.data(), static_cast<int>(tokens.size()), true, true);
    }
    if (n <= 0) return env->NewStringUTF("Could not tokenize prompt.");
    tokens.resize(static_cast<size_t>(n));

    const uint32_t context_limit = llama_n_ctx(ctx);
    if (tokens.size() + 1 >= context_limit)
        return env->NewStringUTF("Prompt exceeds the model context window. Shorten the request or clear older conversation history.");
    const int safe_max_tokens = std::max(1, std::min(static_cast<int>(maxTokens),
        static_cast<int>(context_limit - tokens.size() - 1)));

    llama_memory_clear(llama_get_memory(ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int>(tokens.size()));
    if (llama_decode(ctx, batch) != 0)
        return env->NewStringUTF("Prompt evaluation failed. Try a smaller context or model.");

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    if (sampler == nullptr) return env->NewStringUTF("Could not initialize the local sampler.");
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string output;
    for (int i = 0; i < safe_max_tokens; ++i) {
        const llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[512];
        const int length = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (length > 0) output.append(piece, static_cast<size_t>(length));
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_ai_jarvis_offline_NativeLlama_unload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(llama_mutex);
    if (ctx != nullptr) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
}
