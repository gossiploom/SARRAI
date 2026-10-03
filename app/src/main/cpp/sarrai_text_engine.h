#pragma once

#include <string>

class SarraiTextEngine {
public:
    static SarraiTextEngine& instance();

    std::string generate(const std::string& user_message);

private:
    SarraiTextEngine() = default;
    ~SarraiTextEngine();

    SarraiTextEngine(const SarraiTextEngine&) = delete;
    SarraiTextEngine& operator=(const SarraiTextEngine&) = delete;

    bool ensureLoaded();

    void* model_ = nullptr;
    void* context_ = nullptr;
    void* vocab_ = nullptr;
};
