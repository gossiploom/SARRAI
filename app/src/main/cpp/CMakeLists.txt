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

target_include_directories(sarrai PRIVATE
    "C:/SARRAI/llama.cpp/include"
    "C:/SARRAI/llama.cpp/ggml/include"
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
