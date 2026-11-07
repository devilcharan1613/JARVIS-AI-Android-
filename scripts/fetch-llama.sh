#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/cpp
if [ ! -d app/src/main/cpp/llama.cpp/.git ]; then
 git clone --depth 1 https://github.com/crc-org/llama.cpp.git app/src/main/cpp/llama.cpp
else
 git -C app/src/main/cpp/llama.cpp pull --ff-only
fi
