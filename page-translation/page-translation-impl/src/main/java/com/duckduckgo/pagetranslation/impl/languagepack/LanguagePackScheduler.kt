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

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.CANCELLED
import androidx.work.WorkInfo.State.ENQUEUED
import androidx.work.WorkInfo.State.FAILED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.DOWNLOADED_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.TOTAL_BYTES_KEY
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

sealed interface LanguagePackDownloadState {
    /** No download of the pair is running or recently finished. Says nothing about whether the pack is installed. */
    data object Idle : LanguagePackDownloadState

    data class InProgress(val downloadedBytes: Long, val totalBytes: Long) : LanguagePackDownloadState

    data object Completed : LanguagePackDownloadState

    data class Failed(val reason: Reason) : LanguagePackDownloadState {
        enum class Reason { DOWNLOAD_ERROR, NOT_ENOUGH_STORAGE }
    }
}

interface LanguagePackScheduler {
    /** Joins a download of the same pair that is running or pending. */
    fun download(pair: String)

    /** Starts the download at once, replacing a pending rerun of a download the system stopped. */
    fun retry(pair: String)

    /** Emits [LanguagePackDownloadState.Idle] when no download of the pair is known, including after one is cancelled. */
    fun getDownloadStateFlow(pair: String): Flow<LanguagePackDownloadState>
}

@ContributesBinding(AppScope::class)
class RealLanguagePackScheduler @Inject constructor(
    private val workManager: WorkManager,
) : LanguagePackScheduler {
    override fun download(pair: String) = enqueue(pair, ExistingWorkPolicy.KEEP)

    override fun retry(pair: String) = enqueue(pair, ExistingWorkPolicy.REPLACE)

    override fun getDownloadStateFlow(pair: String): Flow<LanguagePackDownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(getWorkName(pair)).map { infos ->
            infos.lastOrNull()?.toLanguagePackDownloadState() ?: LanguagePackDownloadState.Idle
        }

    private fun enqueue(pair: String, policy: ExistingWorkPolicy) {
        workManager.enqueueUniqueWork(
            getWorkName(pair),
            policy,
            OneTimeWorkRequestBuilder<LanguagePackWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(workDataOf(PAIR_KEY to pair))
                .build(),
        )
    }

    private fun getWorkName(pair: String) = "$WORK_NAME_PREFIX$pair"

    private companion object {
        const val WORK_NAME_PREFIX = "pageTranslationLanguagePack-"
    }
}

internal fun WorkInfo.toLanguagePackDownloadState(): LanguagePackDownloadState = when (state) {
    // a pending rerun means the system stopped an earlier run, which the session reports as a download error
    ENQUEUED, BLOCKED -> if (runAttemptCount > 0) LanguagePackDownloadState.Failed(DOWNLOAD_ERROR) else inProgress()
    RUNNING -> inProgress()
    SUCCEEDED -> LanguagePackDownloadState.Completed
    FAILED -> LanguagePackDownloadState.Failed(
        Reason.entries.firstOrNull { it.name == outputData.getString(FAILURE_REASON_KEY) } ?: DOWNLOAD_ERROR,
    )
    CANCELLED -> LanguagePackDownloadState.Idle
}

private fun WorkInfo.inProgress() = LanguagePackDownloadState.InProgress(
    downloadedBytes = progress.getLong(DOWNLOADED_BYTES_KEY, 0),
    totalBytes = progress.getLong(TOTAL_BYTES_KEY, 0),
)
