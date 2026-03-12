#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ── Model handle ─────────────────────────────────────────────────────────────

struct LlamaHandle {
    llama_model   *model   = nullptr;
    llama_context *ctx     = nullptr;
    llama_sampler *sampler = nullptr;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_duckduckgo_aihistorysearch_impl_LlamaCppInference_nativeLoadModel(
    JNIEnv *env, jobject, jstring jpath, jint nCtx)
{
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("Loading model from %s (nCtx=%d)", path, nCtx);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // CPU-only on Android (no CUDA/Metal)

    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx    = nCtx;
    cparams.n_batch  = 512;
    cparams.n_ubatch = 512;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Simple greedy sampler (deterministic, no randomness for eval)
    llama_sampler *sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    auto *handle = new LlamaHandle{model, ctx, sampler};
    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jstring JNICALL
Java_com_duckduckgo_aihistorysearch_impl_LlamaCppInference_nativeComplete(
    JNIEnv *env, jobject, jlong handlePtr, jstring jprompt, jint maxNewTokens)
{
    auto *h = reinterpret_cast<LlamaHandle *>(handlePtr);
    if (!h) return env->NewStringUTF("");

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);

    // Tokenize prompt
    const llama_vocab *vocab = llama_model_get_vocab(h->model);
    const int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt),
                                         nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_prompt);
    llama_tokenize(vocab, prompt, strlen(prompt),
                   tokens.data(), tokens.size(), true, true);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Clear memory (KV cache) + decode prompt
    llama_memory_clear(llama_get_memory(h->ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(h->ctx, batch)) {
        LOGE("Prompt decode failed");
        return env->NewStringUTF("");
    }

    // Generate tokens
    std::string result;
    for (int i = 0; i < maxNewTokens; i++) {
        llama_token tok = llama_sampler_sample(h->sampler, h->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int n = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        llama_batch next = llama_batch_get_one(&tok, 1);
        if (llama_decode(h->ctx, next)) break;
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_duckduckgo_aihistorysearch_impl_LlamaCppInference_nativeFreeModel(
    JNIEnv *, jobject, jlong handlePtr)
{
    auto *h = reinterpret_cast<LlamaHandle *>(handlePtr);
    if (!h) return;
    llama_sampler_free(h->sampler);
    llama_free(h->ctx);
    llama_model_free(h->model);
    delete h;
    LOGI("Model freed");
}

} // extern "C"
