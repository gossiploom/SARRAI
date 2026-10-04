#include "sarrai_text_engine.h"

#include <android/log.h>
#include <cstdint>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "SARRAI_TEXT"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    constexpr const char * MODEL_PATH =
        "/data/data/com.sarrai.app/files/Qwen3-0.6B-Q4_0.gguf";

    constexpr int32_t CONTEXT_SIZE = 2048;
    constexpr int32_t MAX_GENERATION_TOKENS = 256;

    llama_model * as_model(void * p) {
        return static_cast<llama_model *>(p);
    }

    llama_context * as_context(void * p) {
        return static_cast<llama_context *>(p);
    }

    std::string trim(const std::string & s) {
        const char * ws = " \t\r\n";
        const size_t b = s.find_first_not_of(ws);
        if (b == std::string::npos) return "";
        const size_t e = s.find_last_not_of(ws);
        return s.substr(b, e - b + 1);
    }

    // Qwen3 emits a <think>...</think> reasoning block before its answer.
    // Remove it so only the final answer is shown to the user.
    std::string strip_thinking(const std::string & text) {
        std::string out = text;
        size_t start;
        while ((start = out.find("<think>")) != std::string::npos) {
            const size_t end = out.find("</think>", start);
            if (end == std::string::npos) {
                out.erase(start);
                break;
            }
            out.erase(start, end + 8 - start);
        }
        return trim(out);
    }
}

SarraiTextEngine& SarraiTextEngine::instance() {
    static SarraiTextEngine engine;
    return engine;
}

SarraiTextEngine::~SarraiTextEngine() {
    if (context_ != nullptr) {
        llama_free(as_context(context_));
        context_ = nullptr;
    }

    if (model_ != nullptr) {
        llama_model_free(as_model(model_));
        model_ = nullptr;
    }

    vocab_ = nullptr;
}

bool SarraiTextEngine::ensureLoaded() {
    if (model_ != nullptr && context_ != nullptr && vocab_ != nullptr) {
        return true;
    }

    static bool backend_ready = false;
    if (!backend_ready) {
        LOGI("Initializing llama backend");
        llama_backend_init();
        backend_ready = true;
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;

    LOGI("Loading model: %s", MODEL_PATH);

    llama_model * model =
        llama_model_load_from_file(MODEL_PATH, model_params);

    if (model == nullptr) {
        LOGE("MODEL LOAD FAILED");
        return false;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = CONTEXT_SIZE;
    ctx_params.n_batch = CONTEXT_SIZE;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;

    LOGI("Creating llama context");

    llama_context * ctx =
        llama_init_from_model(model, ctx_params);

    if (ctx == nullptr) {
        LOGE("CONTEXT CREATION FAILED");
        llama_model_free(model);
        return false;
    }

    const llama_vocab * vocab =
        llama_model_get_vocab(model);

    if (vocab == nullptr) {
        LOGE("VOCABULARY ACCESS FAILED");
        llama_free(ctx);
        llama_model_free(model);
        return false;
    }

    model_ = model;
    context_ = ctx;
    vocab_ = const_cast<llama_vocab *>(vocab);

    LOGI("MODEL, CONTEXT AND VOCABULARY READY");

    return true;
}

std::string SarraiTextEngine::generate(
    const std::vector<std::pair<std::string, std::string>>& messages) {

    if (!ensureLoaded()) {
        return "ERROR: SARRAI could not load the local AI model.";
    }

    const llama_model * model = as_model(model_);
    llama_context * ctx = as_context(context_);
    const llama_vocab * vocab =
        static_cast<const llama_vocab *>(vocab_);

    // Start each standalone request with a clean llama memory state.
    llama_memory_t memory = llama_get_memory(ctx);
    if (memory != nullptr) {
        llama_memory_clear(memory, true);
    }

    const char * tmpl =
        llama_model_chat_template(model, nullptr);

    if (tmpl == nullptr) {
        LOGE("NO EMBEDDED CHAT TEMPLATE FOUND");
        return "ERROR: No compatible chat template was found.";
    }

    // Build the complete conversation for the Qwen3 chat template.
    // The Java layer sends every user and assistant turn in order.
    std::vector<std::string> content_storage;
    content_storage.reserve(messages.size());

    std::vector<llama_chat_message> chat_messages;
    chat_messages.reserve(messages.size());

    for (size_t i = 0; i < messages.size(); ++i) {
        content_storage.push_back(messages[i].second);

        // Qwen3 supports /no_think to avoid the long reasoning block.
        // Apply it only to the latest user message.
        if (i == messages.size() - 1 && messages[i].first == "user") {
            content_storage.back() += " /no_think";
        }

        llama_chat_message chat_message{};
        chat_message.role = messages[i].first.c_str();
        chat_message.content = content_storage.back().c_str();
        chat_messages.push_back(chat_message);
    }

    if (chat_messages.empty()) {
        return "ERROR: The conversation is empty.";
    }

    int32_t prompt_size =
        llama_chat_apply_template(
            tmpl,
            chat_messages.data(),
            static_cast<int32_t>(chat_messages.size()),
            true,
            nullptr,
            0);

    if (prompt_size <= 0) {
        LOGE("CHAT TEMPLATE APPLICATION FAILED");
        return "ERROR: Could not format the conversation.";
    }

    std::vector<char> prompt(
        static_cast<size_t>(prompt_size) + 1);

    int32_t formatted_size =
        llama_chat_apply_template(
            tmpl,
            chat_messages.data(),
            static_cast<int32_t>(chat_messages.size()),
            true,
            prompt.data(),
            static_cast<int32_t>(prompt.size()));
    if (formatted_size <= 0) {
        LOGE("CHAT TEMPLATE APPLICATION FAILED");
        return "ERROR: Could not format the conversation.";
    }

    prompt[static_cast<size_t>(formatted_size)] = '\0';

    LOGI("Formatted prompt length: %d", formatted_size);

    int32_t token_count =
        llama_tokenize(
            vocab,
            prompt.data(),
            formatted_size,
            nullptr,
            0,
            false,
            true);

    if (token_count == INT32_MIN || token_count == 0) {
        LOGE("TOKEN COUNT FAILED: %d", token_count);
        return "ERROR: Could not tokenize the prompt.";
    }

    if (token_count < 0) {
        token_count = -token_count;
    }

    std::vector<llama_token> tokens(
        static_cast<size_t>(token_count));

    int32_t actual_tokens =
        llama_tokenize(
            vocab,
            prompt.data(),
            formatted_size,
            tokens.data(),
            token_count,
            false,
            true);

    if (actual_tokens <= 0) {
        LOGE("TOKENIZATION FAILED");
        return "ERROR: Could not tokenize the prompt.";
    }

    tokens.resize(static_cast<size_t>(actual_tokens));

    LOGI("Prompt token count: %d", actual_tokens);

    if (actual_tokens >= CONTEXT_SIZE - 8) {
        LOGE("PROMPT TOO LONG: %d tokens", actual_tokens);
        return "ERROR: The message is too long for SARRAI's memory.";
    }

    const int32_t batch_capacity = actual_tokens > 512 ? actual_tokens : 512;

    llama_batch batch =
        llama_batch_init(batch_capacity, 0, 1);

    if (batch.token == nullptr) {
        LOGE("BATCH INITIALIZATION FAILED");
        return "ERROR: Could not initialize inference batch.";
    }

    for (int32_t i = 0; i < actual_tokens; ++i) {
        batch.token[batch.n_tokens] = tokens[static_cast<size_t>(i)];
        batch.pos[batch.n_tokens] = i;
        batch.n_seq_id[batch.n_tokens] = 1;
        batch.seq_id[batch.n_tokens][0] = 0;
        batch.logits[batch.n_tokens] = (i == actual_tokens - 1);
        batch.n_tokens++;
    }

    int32_t result = llama_decode(ctx, batch);

    if (result != 0) {
        LOGE("PROMPT DECODE FAILED: %d", result);
        llama_batch_free(batch);
        return "ERROR: Prompt evaluation failed.";
    }

    llama_sampler_chain_params sampler_params =
        llama_sampler_chain_default_params();

    llama_sampler * sampler =
        llama_sampler_chain_init(sampler_params);

    if (sampler == nullptr) {
        LOGE("SAMPLER INITIALIZATION FAILED");
        llama_batch_free(batch);
        return "ERROR: Could not initialize sampler.";
    }

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_top_k(40));

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_top_p(0.9f, 1));

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_temp(0.7f));

    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_dist(1234));

    std::string response;
    response.reserve(1024);

    const int32_t n_ctx = static_cast<int32_t>(llama_n_ctx(ctx));
    llama_pos current_pos = actual_tokens;
    bool generation_error = false;
    int32_t generated = 0;

    for (int32_t i = 0; i < MAX_GENERATION_TOKENS; ++i) {

        if (current_pos >= n_ctx) {
            LOGI("Context window full (%d tokens); stopping generation", n_ctx);
            break;
        }

        // Sample from the logits of the last token that was decoded.
        // llama_sampler_sample() also calls llama_sampler_accept() internally.
        llama_token token =
            llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        char piece[256];

        int32_t piece_size =
            llama_token_to_piece(
                vocab,
                token,
                piece,
                sizeof(piece),
                0,
                false);   // do not render special/control tokens as text

        if (piece_size > 0) {
            response.append(piece, static_cast<size_t>(piece_size));
        }

        // Feed the sampled token back into the model as a single-token batch.
        // FIX: n_tokens must be 1. Previously it was reset to 0 and never
        // incremented, so llama_decode() received an empty batch and
        // rejected it with -1 ("GENERATION DECODE FAILED: -1").
        batch.n_tokens = 0;
        batch.token[0] = token;
        batch.pos[0] = current_pos;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;
        batch.n_tokens = 1;

        result = llama_decode(ctx, batch);

        if (result != 0) {
            LOGE("GENERATION DECODE FAILED: %d (pos=%d, token=%d)",
                 result, current_pos, token);
            generation_error = true;
            break;
        }

        ++current_pos;
        ++generated;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    response = strip_thinking(response);

    if (generation_error) {
        LOGE("Generation TERMINATED BY ERROR after %d tokens. Partial length: %zu",
             generated, response.size());
        if (response.empty()) {
            return "ERROR: SARRAI's local AI engine failed while generating a reply.";
        }
        return response + "\n\n[Reply cut short by a local engine error]";
    }

    LOGI("Generation completed successfully. Tokens: %d, response length: %zu",
         generated, response.size());

    if (response.empty()) {
        return "ERROR: SARRAI generated an empty response.";
    }

    return response;
}
