package com.duckduckgo.aihistorysearch.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat

/**
 * Thin Kotlin wrapper around the llama.cpp JNI bridge.
 *
 * Lifecycle: create once, call [complete] as many times as needed, call [close] when done.
 * Not thread-safe — call only from a single coroutine at a time.
 *
 * @param modelPath  Absolute path to a GGUF model file.
 * @param nCtx       Context window size in tokens. 2048 is sufficient for history prompts.
 */
internal class LlamaCppInference(
    private val modelPath: String,
    private val nCtx: Int = 2048,
) : AutoCloseable {

    private var handle: Long = 0L

    /** Loads the model. Must be called before [complete]. Runs on IO dispatcher. */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        if (handle != 0L) return@withContext true
        handle = nativeLoadModel(modelPath, nCtx)
        if (handle == 0L) {
            logcat { "LlamaCppInference: failed to load model at $modelPath" }
        }
        handle != 0L
    }

    /**
     * Runs completion. Returns the generated text (may be empty on failure).
     * Runs on IO dispatcher.
     */
    suspend fun complete(prompt: String, maxNewTokens: Int = 512): String =
        withContext(Dispatchers.IO) {
            if (handle == 0L) return@withContext ""
            nativeComplete(handle, prompt, maxNewTokens)
        }

    override fun close() {
        if (handle != 0L) {
            nativeFreeModel(handle)
            handle = 0L
        }
    }

    // ── JNI declarations ─────────────────────────────────────────────────────

    private external fun nativeLoadModel(path: String, nCtx: Int): Long
    private external fun nativeComplete(handle: Long, prompt: String, maxNewTokens: Int): String
    private external fun nativeFreeModel(handle: Long)

    companion object {
        init {
            System.loadLibrary("llama_jni")
        }
    }
}
