cmake_minimum_required(VERSION 3.22.1)
project(sarrai)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libllama.so")

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libggml.so")

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libggml-cpu.so")

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a/libggml-base.so")

add_library(sarrai SHARED sarrai_jni.cpp sarrai_text_engine.cpp)

# Resolve llama.cpp relative to the repository root so the project builds on
# any machine, not only C:\\SARRAI.
set(LLAMA_CPP_DIR "${CMAKE_SOURCE_DIR}/../../../../llama.cpp")

target_include_directories(sarrai PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/ggml/include"
)

find_library(log-lib log)

target_link_libraries(
    sarrai
    llama
    ggml
    ggml-cpu
    ggml-base
    ${log-lib}
)
