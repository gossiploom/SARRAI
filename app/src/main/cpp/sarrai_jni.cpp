#include <jni.h>
#include <string>
#include <android/log.h>

#include "llama.h"
#include "sarrai_text_engine.h"

#define LOG_TAG "SARRAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_sarrai_app_MainActivity_stringFromJNI(
        JNIEnv* env, jobject /* this */) {

    LOGI("SARRAI JNI function called");

    if (g_model != nullptr && g_ctx != nullptr) {
        LOGI("Model and context already loaded");
        return env->NewStringUTF("SARRAI model already loaded");
    }

    LOGI("Initializing llama backend");
    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    const char * model_path =
        "/data/data/com.sarrai.app/files/Qwen3-0.6B-Q4_0.gguf";

    LOGI("Loading model: %s", model_path);

    g_model = llama_model_load_from_file(model_path, model_params);

    if (g_model == nullptr) {
        LOGE("MODEL LOAD FAILED");
        return env->NewStringUTF("ERROR: Could not load Qwen3 model");
    }

    LOGI("MODEL LOADED SUCCESSFULLY");

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    LOGI("Creating llama context");

    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (g_ctx == nullptr) {
        LOGE("CONTEXT CREATION FAILED");
        llama_model_free(g_model);
        g_model = nullptr;
        return env->NewStringUTF(
            "ERROR: Model loaded but context creation failed");
    }

    LOGI("CONTEXT CREATED SUCCESSFULLY");

    return env->NewStringUTF(
        "SARRAI llama.cpp model loaded successfully");
}


JNIEXPORT jstring JNICALL
Java_com_sarrai_app_ChatActivity_nativeGenerate(
        JNIEnv* env,
        jobject /* this */,
        jstring user_message) {

    if (user_message == nullptr) {
        return env->NewStringUTF(
            "ERROR: Empty message.");
    }

    const char * message_chars =
        env->GetStringUTFChars(user_message, nullptr);

    if (message_chars == nullptr) {
        return env->NewStringUTF(
            "ERROR: Could not read message.");
    }

    std::string message(message_chars);

    env->ReleaseStringUTFChars(
        user_message,
        message_chars);

    LOGI("Generating response for message length: %zu",
         message.size());

    std::string response =
        SarraiTextEngine::instance().generate(message);

    return env->NewStringUTF(response.c_str());
}

}
