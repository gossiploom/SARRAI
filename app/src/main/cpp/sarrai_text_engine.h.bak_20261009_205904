#pragma once

#include <string>
#include <vector>
#include <utility>

class SarraiTextEngine {
public:
    static SarraiTextEngine& instance();

    std::string generate(const std::vector<std::pair<std::string, std::string>>& messages);

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
