/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class TranslationEngine @Inject constructor() {
    private var translator: Translator? = null
    private var onTranslationFinishedListener: (() -> Unit)? = null
    private val downloadedModels = mutableSetOf<String>()
    private val languageIdentifier = LanguageIdentification.getClient()
    private var languagePair: String? = null

    suspend fun setLanguagePair(sourceLanguage: String, targetLanguage: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()

        languagePair = sourceLanguage + targetLanguage
        translator?.close()
        translator = Translation.getClient(options).apply {
            languagePair?.let {
                if (!downloadedModels.contains(it)) {
                    withContext(Dispatchers.IO) {
                        Tasks.await(
                            downloadModelIfNeeded()
                                .addOnSuccessListener { _ ->
                                    downloadedModels.add(it)
                                }
                        )
                    }
                }
            }
        }
    }

    fun setOnTranslationFinishedListener(listener: () -> Unit) {
        onTranslationFinishedListener = listener
    }

    fun onTranslationFinished() {
        try {
            onTranslationFinishedListener?.invoke()
        } catch (e: Exception) {
            Timber.e(e, "$$$ Error invoking onTranslationFinishedListener")
        }
    }

    fun identifyLanguage(text: String): String {
        return Tasks.await(languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                languageCode
            }
            .addOnFailureListener {
                // Model couldn’t be loaded or other internal error.
                // ...
            })
    }

    fun translate(original: String): String {
        try {
            return return translator?.let {
                Tasks.await(
                    it.translate(original).addOnFailureListener { e -> Timber.d(e, "$$$ Error translating") }
                )
            } ?: ""
        } catch (e: Exception) {
            Timber.e(e, "$$$ Failed to translate text")
            return original
        }

    }

    fun onClose() {
        translator?.close()
    }
}
