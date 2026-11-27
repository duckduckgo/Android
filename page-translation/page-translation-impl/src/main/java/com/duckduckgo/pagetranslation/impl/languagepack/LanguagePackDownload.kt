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

package com.duckduckgo.pagetranslation.impl.languagepack

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Downloads the language pack the user confirmed. It knows nothing about tabs: the caller decides what to do with the outcome.
 */
interface LanguagePackDownload {
    /**
     * Starts downloading [pair] if no download of it is running, then suspends until it ends. Cancelling the caller stops the wait,
     * not the download. Returns [LanguagePackDownloadState.Downloaded] or [LanguagePackDownloadState.Failed].
     */
    suspend fun download(pair: LanguagePair): LanguagePackDownloadState

    /**
     * Whether a caller is currently waiting on the download of [pair].
     */
    fun isDownloading(pair: LanguagePair): Boolean
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealLanguagePackDownload @Inject constructor(
    private val scheduler: LanguagePackScheduler,
) : LanguagePackDownload {

    private val inFlight = ConcurrentHashMap.newKeySet<LanguagePair>()

    override suspend fun download(pair: LanguagePair): LanguagePackDownloadState {
        inFlight.add(pair)
        try {
            scheduler.download(pair.key)
            // the flow can replay the terminal state of an earlier download of this pair before the new work shows up
            return scheduler.getDownloadStateFlow(pair.key).dropWhile { !it.isActive() }.firstOrNull { it.isTerminal() }
                ?: LanguagePackDownloadState.Failed(LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR)
        } finally {
            inFlight.remove(pair)
        }
    }

    override fun isDownloading(pair: LanguagePair): Boolean = pair in inFlight
}

fun LanguagePackDownloadState.isDownloaded() = this is LanguagePackDownloadState.Downloaded

private fun LanguagePackDownloadState.isTerminal() = isDownloaded() || this is LanguagePackDownloadState.Failed

private fun LanguagePackDownloadState.isActive() = this is LanguagePackDownloadState.InProgress || this is LanguagePackDownloadState.Interrupted
