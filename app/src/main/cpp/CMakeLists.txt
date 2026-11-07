cmake_minimum_required(VERSION 3.22.1)
project(jarvis_llama LANGUAGES C CXX)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source missing. Run scripts/fetch-llama.sh first.")
endif()

# Portable Android NDK configuration. Keep optional host/server/test features off.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_FATAL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp llama-build)

add_library(jarvis_llama SHARED native-lib.cpp)
target_compile_features(jarvis_llama PRIVATE cxx_std_17)
target_include_directories(jarvis_llama PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include"
)
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(jarvis_llama PRIVATE llama ${log-lib} ${android-lib})
