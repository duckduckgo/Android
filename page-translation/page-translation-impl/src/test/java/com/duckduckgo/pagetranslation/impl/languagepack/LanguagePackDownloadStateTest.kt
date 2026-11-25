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

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import androidx.work.workDataOf
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Downloaded
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason.NOT_ENOUGH_STORAGE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Idle
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.InProgress
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Interrupted
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.DOWNLOADED_BYTES_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackWorker.Companion.TOTAL_BYTES_KEY
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class LanguagePackDownloadStateTest {
    @Test
    fun reportProgressWhileRunning() {
        val progress = workDataOf(DOWNLOADED_BYTES_KEY to 5L, TOTAL_BYTES_KEY to 10L)

        assertEquals(InProgress(5, 10), workInfo(State.RUNNING, progress = progress).toLanguagePackDownloadState())
    }

    @Test
    fun treatFirstPendingRunAsInProgress() {
        assertEquals(InProgress(0, 0), workInfo(State.ENQUEUED).toLanguagePackDownloadState())
    }

    @Test
    fun reportInterruptedWhileWaitingToRunAgain() {
        assertEquals(Interrupted, workInfo(State.ENQUEUED, runAttemptCount = 1).toLanguagePackDownloadState())
    }

    @Test
    fun reportFailureReasonOfACrashedDownload() {
        val output = workDataOf(FAILURE_REASON_KEY to NOT_ENOUGH_STORAGE.name)

        assertEquals(Failed(NOT_ENOUGH_STORAGE), workInfo(State.FAILED, output = output).toLanguagePackDownloadState())
        assertEquals(Failed(DOWNLOAD_ERROR), workInfo(State.FAILED).toLanguagePackDownloadState())
    }

    @Test
    fun reportTheFailureReasonOfADownloadThatSucceededSoItsChainKeepsRunning() {
        val output = workDataOf(FAILURE_REASON_KEY to NOT_ENOUGH_STORAGE.name)

        assertEquals(Failed(NOT_ENOUGH_STORAGE), workInfo(State.SUCCEEDED, output = output).toLanguagePackDownloadState())
    }

    @Test
    fun reportDownloadedOnSuccessAndIdleWhenCancelled() {
        assertEquals(Downloaded, workInfo(State.SUCCEEDED).toLanguagePackDownloadState())
        assertEquals(Idle, workInfo(State.CANCELLED).toLanguagePackDownloadState())
    }

    private fun workInfo(
        state: State,
        output: Data = Data.EMPTY,
        progress: Data = Data.EMPTY,
        runAttemptCount: Int = 0,
    ) = WorkInfo(UUID.randomUUID(), state, emptySet(), output, progress, runAttemptCount)
}
