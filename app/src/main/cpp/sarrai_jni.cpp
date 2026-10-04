#include <jni.h>
#include <string>
#include <android/log.h>

#include "sarrai_text_engine.h"

#define LOG_TAG "SARRAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
    // NewStringUTF expects "modified UTF-8" and can abort on emoji and other
    // 4-byte characters produced by the model. Build the Java string from raw
    // UTF-8 bytes via java.lang.String(byte[], "UTF-8") instead.
    jstring to_jstring(JNIEnv * env, const std::string & utf8) {
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(utf8.size()));
        if (bytes == nullptr) return env->NewStringUTF("ERROR: Out of memory.");
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(utf8.size()),
                                reinterpret_cast<const jbyte *>(utf8.data()));
        jclass string_class = env->FindClass("java/lang/String");
        jmethodID ctor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
        jstring charset = env->NewStringUTF("UTF-8");
        auto result = static_cast<jstring>(env->NewObject(string_class, ctor, bytes, charset));
        env->DeleteLocalRef(charset);
        env->DeleteLocalRef(bytes);
        env->DeleteLocalRef(string_class);
        return result;
    }
}

extern "C" {

// Kept for compatibility. The model is now owned solely by SarraiTextEngine,
// so it is never loaded twice into memory.
JNIEXPORT jstring JNICALL
Java_com_sarrai_app_MainActivity_stringFromJNI(JNIEnv * env, jobject /* this */) {
    LOGI("SARRAI JNI function called");
    return env->NewStringUTF("SARRAI native engine available");
}

JNIEXPORT jstring JNICALL
Java_com_sarrai_app_ChatActivity_nativeGenerate(
        JNIEnv * env,
        jobject /* this */,
        jstring user_message) {

    if (user_message == nullptr) {
        return env->NewStringUTF("ERROR: Empty message.");
    }

    const char * message_chars = env->GetStringUTFChars(user_message, nullptr);
    if (message_chars == nullptr) {
        return env->NewStringUTF("ERROR: Could not read message.");
    }

    std::string message(message_chars);
    env->ReleaseStringUTFChars(user_message, message_chars);

    LOGI("Generating response for message length: %zu", message.size());

    std::string response = SarraiTextEngine::instance().generate(message);
    return to_jstring(env, response);
}

}
