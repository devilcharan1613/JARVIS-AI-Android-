#include <jni.h>
#include <string>
#include <vector>
#include <algorithm>
#include "llama.h"
static llama_model * model = nullptr;
static llama_context * ctx = nullptr;
extern "C" JNIEXPORT jboolean JNICALL Java_ai_jarvis_offline_NativeLlama_loadModel(JNIEnv *env, jobject, jstring path, jint contextSize) {
 if (ctx) { llama_free(ctx); ctx = nullptr; }
 if (model) { llama_model_free(model); model = nullptr; }
 const char * p = env->GetStringUTFChars(path, nullptr);
 llama_model_params mp = llama_model_default_params(); mp.n_gpu_layers = 0;
 model = llama_model_load_from_file(p, mp); env->ReleaseStringUTFChars(path, p);
 if (!model) return JNI_FALSE;
 llama_context_params cp = llama_context_default_params(); cp.n_ctx = (uint32_t)contextSize; cp.n_batch = 256;
 ctx = llama_init_from_model(model, cp);
 if (!ctx) { llama_model_free(model); model = nullptr; return JNI_FALSE; }
 return JNI_TRUE;
}
extern "C" JNIEXPORT jstring JNICALL Java_ai_jarvis_offline_NativeLlama_generate(JNIEnv *env, jobject, jstring input, jint maxTokens) {
 if (!model || !ctx) return env->NewStringUTF("Load a GGUF model first.");
 const char * p = env->GetStringUTFChars(input, nullptr); std::string prompt(p); env->ReleaseStringUTFChars(input, p);
 const llama_vocab * vocab = llama_model_get_vocab(model);
 std::vector<llama_token> tokens(prompt.size() + 16);
 int n = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
 // llama_tokenize returns the required capacity as a negative value when the
 // caller's buffer is too small. Retry instead of rejecting valid long prompts.
 if (n < 0) {
  tokens.resize((size_t)(-n));
  n = llama_tokenize(vocab, prompt.c_str(), (int)prompt.size(), tokens.data(), (int)tokens.size(), true, true);
 }
 if (n <= 0) return env->NewStringUTF("Could not tokenize prompt.");
 tokens.resize((size_t)n);
 const uint32_t contextLimit = llama_n_ctx(ctx);
 if (tokens.size() + 1 >= contextLimit) return env->NewStringUTF("Prompt exceeds the model context window. Shorten the request.");
 const int safeMaxTokens = std::max(1, std::min((int)maxTokens, (int)(contextLimit - tokens.size() - 1)));
 llama_memory_clear(llama_get_memory(ctx), true);
 llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
 if (llama_decode(ctx, batch) != 0) return env->NewStringUTF("Prompt evaluation failed.");
 auto sp = llama_sampler_chain_default_params(); llama_sampler * sampler = llama_sampler_chain_init(sp);
 llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
 llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.8f));
 llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
 std::string output;
 for (int i=0; i<safeMaxTokens; ++i) {
  llama_token tok = llama_sampler_sample(sampler, ctx, -1);
  if (llama_vocab_is_eog(vocab, tok)) break;
  char piece[256]; int len = llama_token_to_piece(vocab, tok, piece, sizeof(piece), 0, true);
  if (len > 0) output.append(piece, len);
  batch = llama_batch_get_one(&tok, 1); if (llama_decode(ctx, batch) != 0) break;
 }
 llama_sampler_free(sampler); return env->NewStringUTF(output.c_str());
}
extern "C" JNIEXPORT void JNICALL Java_ai_jarvis_offline_NativeLlama_unload(JNIEnv *, jobject) {
 if (ctx) { llama_free(ctx); ctx = nullptr; } if (model) { llama_model_free(model); model = nullptr; }
}
