/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.pagetranslation.impl.translatekit

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.translatekit.LanguageResult
import com.duckduckgo.translatekit.ModelSpec
import com.duckduckgo.translatekit.TranslateKit
import com.duckduckgo.translatekit.TranslationModel
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** translate-kit's native calls, which all block, so call them off the main thread. */
interface TranslateKitLibrary {
    /**
     * Loads translate-kit once and returns whether it's available, which it never is while page translation is off or on a device
     * it doesn't ship for.
     */
    fun init(): Boolean

    @Throws(IOException::class)
    fun decompressZstd(compressedFile: File, outputFile: File)

    fun detectLanguage(text: String): LanguageResult

    /** Throws [IllegalArgumentException] when the language pack's files can't be loaded. */
    fun loadTranslator(spec: ModelSpec): Translator
}

/** Translates with one language pack's model, which holds native memory until it's closed. */
interface Translator {
    /**
     * Returns one result per input: the translated text, or for HTML translate-kit's JSON tree of the translated HTML, which is
     * the JSON literal `null` when the translated HTML is malformed. Throws when any input fails.
     */
    fun translate(inputs: List<String>, isHtml: Boolean): List<String>

    fun close()
}

@Suppress("DenyListedApi", "unused")
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTranslateKitLibrary @Inject constructor(
    private val context: Context,
    private val feature: PageTranslationFeature,
) : TranslateKitLibrary {
    // translate-kit doesn't remember a failed native init, so it would try again, and fail again, on every call
    @Volatile
    private var hasInitFailed = false

    override fun init(): Boolean {
        if (!feature.translation().isEnabled() || hasInitFailed) return false

        return try {
            TranslateKit.init(context)
            TranslateKit.isInitialized()
        } catch (e: IllegalStateException) {
            hasInitFailed = true
            logcat(ERROR) { "TranslateKit failed to initialize: ${e.asLog()}" }
            false
        }
    }

    override fun decompressZstd(compressedFile: File, outputFile: File) = TranslateKit.decompressZstd(compressedFile.path, outputFile.path)

    override fun detectLanguage(text: String): LanguageResult = TranslateKit.detectLanguage(text)

    override fun loadTranslator(spec: ModelSpec): Translator = RealTranslator(TranslateKit.loadModel(spec))

    private class RealTranslator(private val model: TranslationModel) : Translator {
        override fun translate(inputs: List<String>, isHtml: Boolean): List<String> =
            model.translate(inputs, isHtml, htmlAsJsonTree = isHtml).map { it.text }

        override fun close() = model.close()
    }
}
