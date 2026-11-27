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
import androidx.work.OneTimeWorkRequest
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
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.DOWNLOADED_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.TOTAL_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE_OLD
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.UNDO_DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileWorker.Companion.OPERATION_KEY
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LanguagePackDownloadState {

    data object Idle : LanguagePackDownloadState

    data class InProgress(val downloadedBytes: Long, val totalBytes: Long) : LanguagePackDownloadState

    data object Interrupted : LanguagePackDownloadState

    data object Downloaded : LanguagePackDownloadState

    data class Failed(val reason: Reason) : LanguagePackDownloadState {
        enum class Reason { DOWNLOAD_ERROR, NOT_ENOUGH_STORAGE }
    }
}

interface LanguagePackScheduler {
    /**
     * Joins a running or pending download of the same pair, unless a delete or removal is queued after it.
     * Otherwise downloads after the pair's pending operations.
     */
    suspend fun download(pair: String)

    /**
     * Starts the download at once, replacing a pending rerun of a download the system stopped.
     * Does nothing when a delete or removal is queued after that download.
     */
    suspend fun retry(pair: String)

    /** Uninstalls the pair's pack, keeping it until [confirmDelete] so [undoDelete] can restore it. */
    fun delete(pair: String)

    /** Restores the pack [delete] removed, unless another operation on the pair started since. */
    fun undoDelete(pair: String)

    /** Removes the pack [delete] kept for [undoDelete]. */
    fun confirmDelete(pair: String)

    /** Removes every installed or deleted pack whose pair isn't in [listedPairs]. Reads the disk, so call it off the main thread. */
    fun removeUnlisted(listedPairs: Set<String>)

    /** Removes the packs [delete] kept that were never confirmed or restored. Reads the disk, so call it off the main thread. */
    fun removeLeftoverOldDirectories()

    /** Emits [LanguagePackDownloadState.Idle] when no download of the pair is known, including after one is cancelled. */
    fun getDownloadStateFlow(pair: String): Flow<LanguagePackDownloadState>
}

@ContributesBinding(AppScope::class)
class RealLanguagePackScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val feature: PageTranslationFeature,
    private val deviceSupport: PageTranslationDeviceSupport,
    private val directories: LanguagePackDirectories,
) : LanguagePackScheduler {
    override suspend fun download(pair: String) {
        if (!canDownload()) return

        val infos = getWorkInfos(pair)
        val newestDownload = infos.getNewestDownload()
        if (newestDownload != null && !newestDownload.state.isFinished && !infos.hasUninstallAfter(newestDownload)) return

        enqueue(pair, createDownloadRequest(pair))
    }

    override suspend fun retry(pair: String) {
        if (!canDownload()) return

        // REPLACE cancels every unfinished work of the pair, so it would also cancel the uninstall
        val infos = getWorkInfos(pair)
        val newestDownload = infos.getNewestDownload()
        if (newestDownload != null && infos.hasUninstallAfter(newestDownload)) return

        workManager.enqueueUniqueWork(getWorkName(pair), ExistingWorkPolicy.REPLACE, createDownloadRequest(pair))
    }

    override fun delete(pair: String) = enqueueFileOperation(pair, DELETE)

    override fun undoDelete(pair: String) = enqueueFileOperation(pair, UNDO_DELETE)

    override fun confirmDelete(pair: String) = enqueueFileOperation(pair, REMOVE_OLD)

    override fun removeUnlisted(listedPairs: Set<String>) {
        (directories.getInstalledPairs() + directories.getPairsWithOldDirectory() - listedPairs).forEach { enqueueFileOperation(it, REMOVE) }
    }

    override fun removeLeftoverOldDirectories() {
        directories.getPairsWithOldDirectory().forEach { enqueueFileOperation(it, REMOVE_OLD) }
    }

    override fun getDownloadStateFlow(pair: String): Flow<LanguagePackDownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(getWorkName(pair)).map { infos ->
            infos.getNewestDownload()?.toLanguagePackDownloadState() ?: LanguagePackDownloadState.Idle
        }

    private suspend fun getWorkInfos(pair: String) = workManager.getWorkInfosForUniqueWorkFlow(getWorkName(pair)).firstOrNull().orEmpty()

    private fun List<WorkInfo>.getNewestDownload() = filter { it.isDownload() }.maxByOrNull { it.order }

    private fun List<WorkInfo>.hasUninstallAfter(download: WorkInfo) = any { it.isUninstall() && it.order > download.order }

    // before Android 12 a queued job would show its download notification before the worker sees the feature is off
    private fun canDownload() = feature.translation().isEnabled() && deviceSupport.isSupported()

    private fun createDownloadRequest(pair: String) = OneTimeWorkRequestBuilder<LanguagePackDownloadWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(workDataOf(PAIR_KEY to pair))
        .addTag(DOWNLOAD_TAG)
        .addTag("$ORDER_TAG_PREFIX${nextOrder()}")
        .build()

    private fun enqueueFileOperation(pair: String, operation: LanguagePackFileOperation) = enqueue(
        pair,
        OneTimeWorkRequestBuilder<LanguagePackFileWorker>()
            .setInputData(workDataOf(PAIR_KEY to pair, OPERATION_KEY to operation.name))
            .addTag("$OPERATION_TAG_PREFIX${operation.name}")
            .addTag("$ORDER_TAG_PREFIX${nextOrder()}")
            .build(),
    )

    // APPEND would fail every operation queued after a failed download, so a failed or cancelled chain is replaced instead
    private fun enqueue(pair: String, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(getWorkName(pair), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private fun WorkInfo.isDownload() = DOWNLOAD_TAG in tags

    private fun WorkInfo.isUninstall() = "$OPERATION_TAG_PREFIX${DELETE.name}" in tags || "$OPERATION_TAG_PREFIX${REMOVE.name}" in tags

    // a chain's infos come in no particular order
    private val WorkInfo.order: Long
        get() = tags.firstOrNull { it.startsWith(ORDER_TAG_PREFIX) }?.removePrefix(ORDER_TAG_PREFIX)?.toLongOrNull() ?: 0

    private fun getWorkName(pair: String) = "$WORK_NAME_PREFIX$pair"

    private companion object {
        const val WORK_NAME_PREFIX = "pageTranslationLanguagePack-"
        const val DOWNLOAD_TAG = "pageTranslationLanguagePackDownload"
        const val OPERATION_TAG_PREFIX = "pageTranslationLanguagePackOperation-"
        const val ORDER_TAG_PREFIX = "pageTranslationLanguagePackOrder-"

        private val lastOrder = AtomicLong(0)

        // seeded from the clock so works queued before a restart stay older than the ones queued after it
        fun nextOrder() = lastOrder.updateAndGet { maxOf(it + 1, System.currentTimeMillis()) }
    }
}

internal fun WorkInfo.toLanguagePackDownloadState(): LanguagePackDownloadState = when (state) {
    ENQUEUED, BLOCKED -> if (runAttemptCount > 0) LanguagePackDownloadState.Interrupted else inProgress()
    RUNNING -> inProgress()
    SUCCEEDED -> getFailureReason()?.let { LanguagePackDownloadState.Failed(it) } ?: LanguagePackDownloadState.Downloaded
    FAILED -> LanguagePackDownloadState.Failed(getFailureReason() ?: DOWNLOAD_ERROR)
    CANCELLED -> LanguagePackDownloadState.Idle
}

private fun WorkInfo.getFailureReason() = Reason.entries.firstOrNull { it.name == outputData.getString(FAILURE_REASON_KEY) }

private fun WorkInfo.inProgress() = LanguagePackDownloadState.InProgress(
    downloadedBytes = progress.getLong(DOWNLOADED_BYTES_KEY, 0),
    totalBytes = progress.getLong(TOTAL_BYTES_KEY, 0),
)
