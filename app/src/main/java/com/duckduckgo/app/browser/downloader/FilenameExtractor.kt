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
import com.duckduckgo.app.browser.downloader.FileDownloader.PendingFileDownload
import com.duckduckgo.app.browser.downloader.FilenameExtractor.GuessQuality.NotGoodEnough
import com.duckduckgo.app.browser.downloader.FilenameExtractor.GuessQuality.TriedAllOptions
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import timber.log.Timber
import javax.inject.Inject

class FilenameExtractor @Inject constructor(
    private val pixel: Pixel
) {


    fun extract(pendingDownload: PendingFileDownload): FilenameExtractionResult {
        val url = pendingDownload.url
        val mimeType = pendingDownload.mimeType
        val contentDisposition = pendingDownload.contentDisposition

        val firstGuess = guessFilename(url, contentDisposition, mimeType)
        val guesses = Guesses(bestGuess = null, latestGuess = firstGuess)
        val baseUrl = url.toUri().host ?: ""
        var pathSegments = pathSegments(url)

        while (evaluateGuessQuality(guesses, pathSegments) != TriedAllOptions) {
            pathSegments = pathSegments.dropLast(1)
            guesses.latestGuess = guessFilename(baseUrl + "/" + pathSegments.rebuildUrl(), contentDisposition, mimeType)
        }

        return bestGuess(guesses)
    }

    private fun evaluateGuessQuality(guesses: Guesses, pathSegments: List<String>): GuessQuality {
        val latestGuess = guesses.latestGuess

        // if it contains a '.' then it's a good chance of a filetype and we can update our best guess
        if (latestGuess.contains(".")) {
            guesses.bestGuess = latestGuess
        }

        if (pathSegments.size < 2) return TriedAllOptions

        return NotGoodEnough
    }

    private fun guessFilename(url: String, contentDisposition: String?, mimeType: String?): String {
        val tidiedUrl = url.removeSuffix("/")
        var guessedFilename = URLUtil.guessFileName(tidiedUrl, contentDisposition, mimeType)

        // we only want to keep the default .bin filetype on the guess if the URL actually has that too
        if (guessedFilename.endsWith(DEFAULT_FILE_TYPE) && !tidiedUrl.endsWith(DEFAULT_FILE_TYPE)) {
            guessedFilename = guessedFilename.removeSuffix(DEFAULT_FILE_TYPE)
        }

        Timber.v("From URL [$url], guessed [$guessedFilename]")
        return guessedFilename
    }

    private fun bestGuess(guesses: Guesses): FilenameExtractionResult {
        val guess = guesses.bestGuess ?: guesses.latestGuess
        if (!guess.contains(".")) {
            pixel.fire(AppPixelName.DOWNLOAD_FILE_DEFAULT_GUESSED_NAME)
            return FilenameExtractionResult.Guess(guess)
        }
        return FilenameExtractionResult.Extracted(guess)
    }

    private fun pathSegments(url: String): List<String> {
        return url.toUri().pathSegments ?: emptyList()
    }

    private fun List<String>.rebuildUrl(): String {
        return joinToString(separator = "/")
    }

    companion object {
        private const val DEFAULT_FILE_TYPE = ".bin"
    }

    sealed class GuessQuality {
        object NotGoodEnough : GuessQuality()
        object TriedAllOptions : GuessQuality()
    }

    sealed class FilenameExtractionResult {
        data class Extracted(val filename: String) : FilenameExtractionResult()
        data class Guess(val bestGuess: String) : FilenameExtractionResult()
    }

    data class Guesses(var latestGuess: String, var bestGuess: String? = null)

}
