/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.downloader

import android.webkit.URLUtil
import androidx.core.net.toUri
import com.duckduckgo.app.browser.downloader.FilenameExtractor.GuessQuality.*
import timber.log.Timber
import javax.inject.Inject

class FilenameExtractor @Inject constructor() {

    fun extract(url: String, contentDisposition: String?, mimeType: String?): String {
        val firstGuess = guessFromUrl(url, contentDisposition, mimeType)
        var pathSegments = pathSegments(url)
        var bestGuess = firstGuess

        while (true) {

            when (determineGuessQuality(bestGuess, pathSegments)) {
                is GoodEnough -> return bestGuess
                is OutOfOptions -> return firstGuess
                is NotGoodEnough -> {
                    pathSegments = pathSegments.dropLast(1)
                    bestGuess = guessFromUrl(pathSegments.rebuildUrl(), contentDisposition, mimeType)
                }
            }

        }
    }

    private fun determineGuessQuality(guess: String, pathSegments: List<String>): GuessQuality {
        Timber.v("Best guess is now $guess")

        if (!guess.endsWith(DEFAULT_FILE_TYPE)) return GoodEnough
        if (pathSegments.isEmpty()) return OutOfOptions

        return NotGoodEnough
    }

    private fun guessFromUrl(url: String, contentDisposition: String?, mimeType: String?): String {
        return URLUtil.guessFileName(url, contentDisposition, mimeType)
    }

    private fun pathSegments(url: String): List<String> {
        val uri = url.toUri()
        return uri.pathSegments ?: emptyList()
    }

    private fun List<String>.rebuildUrl(): String {
        return joinToString(separator = "/")
    }

    companion object {
        private const val DEFAULT_FILE_TYPE = ".bin"
    }

    sealed class GuessQuality {
        object GoodEnough : GuessQuality()
        object NotGoodEnough : GuessQuality()
        object OutOfOptions : GuessQuality()
    }

}
