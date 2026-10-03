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

    LOGI("Initializing llama backend");
    llama_backend_init();

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
    ctx_params.n_batch = 512;
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
    const std::string& user_message) {

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

    llama_chat_message message{};
    message.role = "user";
    message.content = user_message.c_str();

    int32_t prompt_size =
        llama_chat_apply_template(
            tmpl,
            &message,
            1,
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
            &message,
            1,
            true,
            prompt.data(),
            static_cast<int32_t>(prompt.size()));

    if (formatted_size < 0) {
        LOGE("CHAT TEMPLATE FORMATTING FAILED");
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

    llama_batch batch =
        llama_batch_init(512, 0, 1);

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

    llama_pos current_pos = actual_tokens;

    for (int32_t i = 0; i < MAX_GENERATION_TOKENS; ++i) {

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
                true);

        if (piece_size > 0) {
            response.append(piece, static_cast<size_t>(piece_size));
        }

        batch.n_tokens = 0;

        batch.token[0] = token;
        batch.pos[0] = current_pos++;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = 1;

        result = llama_decode(ctx, batch);

        if (result != 0) {
            LOGE("GENERATION DECODE FAILED: %d", result);
            break;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    LOGI("Generation complete. Response length: %zu",
         response.size());

    if (response.empty()) {
        return "ERROR: SARRAI generated an empty response.";
    }

    return response;
}
