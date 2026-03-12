cmake_minimum_required(VERSION 3.22.1)
project(llama_jni)

set(CMAKE_CXX_STANDARD 17)

# Disable unnecessary llama.cpp targets
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# llama.cpp source (one level up from this module)
add_subdirectory(../llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama_build)

# Our JNI bridge
add_library(llama_jni SHARED src/main/cpp/llama_jni.cpp)

target_include_directories(llama_jni PRIVATE
    ../llama.cpp/include
    ../llama.cpp/ggml/include
)

target_link_libraries(llama_jni
    llama
    ggml
    android
    log
)
