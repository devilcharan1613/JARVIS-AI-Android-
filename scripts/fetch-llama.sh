#!/usr/bin/env bash
set -euo pipefail

LLAMA_DIR="app/src/main/cpp/llama.cpp"
LLAMA_REPO="https://github.com/crc-org/llama.cpp.git"
# Pin the native runtime so future upstream changes cannot silently break builds.
LLAMA_COMMIT="d67341dc18fc5cc63362880ab2f8f9ecfc7932e7"

mkdir -p app/src/main/cpp
if [ ! -d "${LLAMA_DIR}/.git" ]; then
    git clone --no-checkout "${LLAMA_REPO}" "${LLAMA_DIR}"
fi

git -C "${LLAMA_DIR}" fetch --depth 1 origin "${LLAMA_COMMIT}"
git -C "${LLAMA_DIR}" checkout --detach FETCH_HEAD

test "$(git -C "${LLAMA_DIR}" rev-parse HEAD)" = "${LLAMA_COMMIT}"
