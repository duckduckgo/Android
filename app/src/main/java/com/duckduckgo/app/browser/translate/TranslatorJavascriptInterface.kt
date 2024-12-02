/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.webkit.JavascriptInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import timber.log.Timber

class TranslatorJavascriptInterface {
    val options = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.SLOVAK)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()
    val translator = Translation.getClient(options)

    @JavascriptInterface
    fun translate(originalText: String): String {
        return mlKitTranslate(originalText)
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "WebTranslator"
    }

    private fun mlKitTranslate(original: String): String {
        return Tasks.await(
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    Timber.d("$$$ Translation model downloaded")
                }
                .addOnFailureListener { exception ->
                    Timber.d("$$$ Failed to download model: $exception")
                }
                .continueWithTask { task ->
                    if (task.isSuccessful) {
                        translator.translate(original)
                            .addOnSuccessListener { translation ->
                                translation
                            }
                            .addOnFailureListener { p0 -> Timber.d("$$$ Failed to translate: $p0") }
                    } else {
                        Tasks.forException<String>(
                            task.exception ?: Exception("Unknown error"),
                        )
                    }
                },
        )
    }
}
