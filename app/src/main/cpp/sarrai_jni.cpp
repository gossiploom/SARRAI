#include <jni.h>
#include <string>
#include <vector>
#include <utility>
#include <android/log.h>

#include "sarrai_text_engine.h"

#define LOG_TAG "SARRAI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
    // Convert a Java String to real UTF-8 bytes.
    // This avoids the modified-UTF-8 behavior of GetStringUTFChars(),
    // which is important for emoji and other non-BMP characters.
    std::string jstring_to_utf8(JNIEnv * env, jstring value) {
        if (value == nullptr) {
            return {};
        }

        jclass string_class = env->FindClass("java/lang/String");
        if (string_class == nullptr) {
            return {};
        }

        jmethodID get_bytes = env->GetMethodID(
                string_class,
                "getBytes",
                "(Ljava/lang/String;)[B");

        if (get_bytes == nullptr) {
            env->DeleteLocalRef(string_class);
            return {};
        }

        jstring charset = env->NewStringUTF("UTF-8");
        if (charset == nullptr) {
            env->DeleteLocalRef(string_class);
            return {};
        }

        auto bytes = static_cast<jbyteArray>(
                env->CallObjectMethod(value, get_bytes, charset));

        env->DeleteLocalRef(charset);
        env->DeleteLocalRef(string_class);

        if (bytes == nullptr) {
            return {};
        }

        jsize length = env->GetArrayLength(bytes);
        if (length <= 0) {
            env->DeleteLocalRef(bytes);
            return {};
        }

        jbyte * data = env->GetByteArrayElements(bytes, nullptr);
        if (data == nullptr) {
            env->DeleteLocalRef(bytes);
            return {};
        }

        std::string result(
                reinterpret_cast<const char *>(data),
                static_cast<size_t>(length));

        env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
        env->DeleteLocalRef(bytes);

        return result;
    }

    // NewStringUTF expects "modified UTF-8" and can abort on emoji and other
    // 4-byte characters produced by the model. Build the Java string from raw
    // UTF-8 bytes via java.lang.String(byte[], "UTF-8") instead.
    jstring to_jstring(JNIEnv * env, const std::string & utf8) {
        jbyteArray bytes = env->NewByteArray(static_cast<jsize>(utf8.size()));
        if (bytes == nullptr) return env->NewStringUTF("ERROR: Out of memory.");

        env->SetByteArrayRegion(
                bytes,
                0,
                static_cast<jsize>(utf8.size()),
                reinterpret_cast<const jbyte *>(utf8.data()));

        jclass string_class = env->FindClass("java/lang/String");
        jmethodID ctor = env->GetMethodID(
                string_class,
                "<init>",
                "([BLjava/lang/String;)V");

        jstring charset = env->NewStringUTF("UTF-8");

        auto result = static_cast<jstring>(
                env->NewObject(string_class, ctor, bytes, charset));

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
Java_com_sarrai_app_MainActivity_stringFromJNI(
        JNIEnv * env,
        jobject /* this */) {

    LOGI("SARRAI JNI function called");
    return env->NewStringUTF("SARRAI native engine available");
}

JNIEXPORT jstring JNICALL
Java_com_sarrai_app_ChatActivity_nativeGenerate(
        JNIEnv * env,
        jobject /* this */,
        jobjectArray roles,
        jobjectArray contents) {

    if (roles == nullptr || contents == nullptr) {
        return env->NewStringUTF("ERROR: Empty conversation.");
    }

    const jsize role_count = env->GetArrayLength(roles);
    const jsize content_count = env->GetArrayLength(contents);

    if (role_count <= 0 || content_count <= 0 || role_count != content_count) {
        return env->NewStringUTF("ERROR: Invalid conversation data.");
    }

    std::vector<std::pair<std::string, std::string>> conversation;
    conversation.reserve(static_cast<size_t>(role_count));

    for (jsize i = 0; i < role_count; ++i) {

        auto role = static_cast<jstring>(
                env->GetObjectArrayElement(roles, i));

        auto content = static_cast<jstring>(
                env->GetObjectArrayElement(contents, i));

        if (role == nullptr || content == nullptr) {
            if (role != nullptr) {
                env->DeleteLocalRef(role);
            }
            if (content != nullptr) {
                env->DeleteLocalRef(content);
            }
            return env->NewStringUTF("ERROR: Invalid conversation message.");
        }

        std::string role_utf8 = jstring_to_utf8(env, role);
        std::string content_utf8 = jstring_to_utf8(env, content);

        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);

        if (role_utf8.empty() || content_utf8.empty()) {
            return env->NewStringUTF("ERROR: Empty conversation message.");
        }

        conversation.emplace_back(
                std::move(role_utf8),
                std::move(content_utf8));
    }

    LOGI(
            "Generating response from conversation messages: %d",
            static_cast<int>(conversation.size()));

    std::string response =
            SarraiTextEngine::instance().generate(conversation);

    return to_jstring(env, response);
}

}