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

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State.BLOCKED
import androidx.work.WorkInfo.State.RUNNING
import androidx.work.WorkInfo.State.SUCCEEDED
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason.DOWNLOAD_ERROR
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.FAILURE_REASON_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE_IF_CORRUPTED
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE_OLD
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.UNDO_DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileWorker.Companion.OPERATION_KEY
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LanguagePackSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private val directories = RealLanguagePackDirectories(context)
    private var isDeviceSupported = true
    private var isWorkFinishing = false

    // the test holds it because WorkManager fails a worker whose suspended coroutine nothing references once it's garbage collected
    private val runningWorkResult = CompletableDeferred<ListenableWorker.Result>()
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: RealLanguagePackScheduler

    @Before
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = spy(WorkManager.getInstance(context))
        feature.translation().setRawStoredState(State(enable = true))
        val deviceSupport = object : PageTranslationDeviceSupport {
            override fun isSupported() = isDeviceSupported
        }
        scheduler = RealLanguagePackScheduler(workManager, feature, deviceSupport, directories)
    }

    @After
    fun tearDown() {
        context.filesDir.resolve("page-translation").deleteRecursively()
    }

    @Test
    fun joinADownloadOfTheSamePairThatIsRunning() = runTest {
        scheduler.download(PAIR)
        val running = getActiveWork(PAIR).single().id

        scheduler.download(PAIR)

        assertEquals(listOf(running), getActiveWork(PAIR).map { it.id })
    }

    @Test
    fun joinADownloadOfTheSamePairThatIsQueuedAfterADelete() = runTest {
        scheduler.delete(PAIR)
        scheduler.download(PAIR)

        scheduler.download(PAIR)

        assertEquals(2, getActiveWork(PAIR).size)
    }

    @Test
    fun downloadAgainWhenACorruptionCheckIsQueuedAfterAnUnfinishedDownload() = runTest {
        scheduler.download(PAIR)
        scheduler.removeIfCorrupted(PAIR)

        scheduler.download(PAIR)

        assertEquals(2, getActiveWork(PAIR).count { it.isDownload() })
    }

    @Test
    fun downloadAgainWhenADeleteIsQueuedAfterAnUnfinishedDownload() = runTest {
        scheduler.download(PAIR)
        scheduler.delete(PAIR)

        scheduler.download(PAIR)

        assertEquals(2, getActiveWork(PAIR).count { it.isDownload() })
    }

    @Test
    fun joinAnUnfinishedDownloadWhenAConfirmedDeleteIsQueuedAfterIt() = runTest {
        scheduler.download(PAIR)
        val download = getActiveWork(PAIR).single { it.isDownload() }
        scheduler.confirmDelete(PAIR)

        scheduler.download(PAIR)

        assertEquals(listOf(download.id), getActiveWork(PAIR).filter { it.isDownload() }.map { it.id })
        assertEquals(listOf(PAIR to REMOVE_OLD), getQueuedOperations())
    }

    @Test
    fun runOperationsQueuedAfterADownloadThatReportsAFailure() = runTest {
        scheduler.download(PAIR)
        scheduler.delete(PAIR)
        isWorkFinishing = true

        runningWorkResult.complete(ListenableWorker.Result.success(workDataOf(FAILURE_REASON_KEY to DOWNLOAD_ERROR.name)))

        val work = workManager.getWorkInfosForUniqueWorkFlow("$WORK_NAME_PREFIX${PAIR.key}").first { infos -> infos.all { it.state.isFinished } }
        assertEquals(listOf(SUCCEEDED, SUCCEEDED), work.map { it.state })
        assertEquals(LanguagePackDownloadState.Failed(DOWNLOAD_ERROR), scheduler.getDownloadStateFlow(PAIR).first())
    }

    @Test
    fun downloadAfterAPendingDeleteOfTheSamePair() = runTest {
        scheduler.delete(PAIR)

        scheduler.download(PAIR)

        assertEquals(BLOCKED, getActiveWork(PAIR).single { it.isDownload() }.state)
        assertEquals(listOf(PAIR to DELETE), getQueuedOperations())
    }

    @Test
    fun replaceTheDownloadOnRetry() = runTest {
        scheduler.download(PAIR)
        val stopped = getActiveWork(PAIR).single().id

        scheduler.retry(PAIR)

        val replacement = getActiveWork(PAIR).single().id
        assertNotEquals(stopped, replacement)
    }

    @Test
    fun keepTheQueuedDeleteOnRetry() = runTest {
        scheduler.download(PAIR)
        val stopped = getActiveWork(PAIR).single().id
        scheduler.delete(PAIR)

        scheduler.retry(PAIR)

        assertEquals(listOf(stopped), getActiveWork(PAIR).filter { it.isDownload() }.map { it.id })
        assertEquals(listOf(PAIR to DELETE), getQueuedOperations())
    }

    @Test
    fun runDownloadsOfDifferentPairsSeparately() = runTest {
        scheduler.download(PAIR)
        scheduler.download(OTHER_PAIR)

        assertEquals(1, getActiveWork(PAIR).size)
        assertEquals(1, getActiveWork(OTHER_PAIR).size)
    }

    @Test
    fun queueNothingWhenTranslationIsOff() = runTest {
        feature.translation().setRawStoredState(State(enable = false))

        scheduler.download(PAIR)
        scheduler.retry(PAIR)

        assertEquals(emptyList<WorkInfo>(), getActiveWork(PAIR))
    }

    @Test
    fun queueNothingOnUnsupportedDevices() = runTest {
        isDeviceSupported = false

        scheduler.download(PAIR)
        scheduler.retry(PAIR)

        assertEquals(emptyList<WorkInfo>(), getActiveWork(PAIR))
    }

    @Test
    fun queueFileOperationsAfterAnUnfinishedDownloadOfThePair() = runTest {
        scheduler.download(PAIR)

        scheduler.delete(PAIR)
        scheduler.undoDelete(PAIR)
        scheduler.confirmDelete(PAIR)

        val (downloads, operations) = getActiveWork(PAIR).partition { it.isDownload() }
        assertEquals(RUNNING, downloads.single().state)
        assertEquals(listOf(BLOCKED, BLOCKED, BLOCKED), operations.map { it.state })
        assertEquals(listOf(PAIR to DELETE, PAIR to UNDO_DELETE, PAIR to REMOVE_OLD), getQueuedOperations())
    }

    @Test
    fun queueFileOperationsWhenTranslationIsOff() {
        feature.translation().setRawStoredState(State(enable = false))

        scheduler.delete(PAIR)

        assertEquals(listOf(PAIR to DELETE), getQueuedOperations())
    }

    @Test
    fun reportNoDownloadWhenOnlyADeleteIsQueued() = runTest {
        scheduler.delete(PAIR)

        assertEquals(LanguagePackDownloadState.Idle, scheduler.getDownloadStateFlow(PAIR).first())
    }

    @Test
    fun reportTheNewestDownloadOfThePair() = runTest {
        isWorkFinishing = true
        scheduler.download(PAIR)
        scheduler.delete(PAIR)
        isWorkFinishing = false

        scheduler.download(PAIR)

        assertEquals(listOf(RUNNING, SUCCEEDED), getAllWork(PAIR).filter { it.isDownload() }.map { it.state }.sorted())
        val enqueuedIds = getEnqueuedRequests().map { it.id }
        listOf(false, true).forEach { isNewestFirst ->
            doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                (invocation.callRealMethod() as Flow<List<WorkInfo>>).map { infos ->
                    infos.sortedBy { enqueuedIds.indexOf(it.id) }.let { if (isNewestFirst) it.reversed() else it }
                }
            }.whenever(workManager).getWorkInfosForUniqueWorkFlow(any())

            assertEquals(LanguagePackDownloadState.InProgress(0, 0), scheduler.getDownloadStateFlow(PAIR).first())
        }
    }

    @Test
    fun removeInstalledAndDeletedPairsMissingFromTheList() {
        directories.getPackDirectory(PAIR).mkdirs()
        directories.getOldDirectory(PAIR).mkdirs()
        directories.getPackDirectory(OTHER_PAIR).mkdirs()
        directories.getStagingDirectory(STAGED_PAIR).mkdirs()
        directories.getOldDirectory(DELETED_PAIR).mkdirs()

        scheduler.removeUnlisted(setOf(PAIR))

        assertEquals(setOf(OTHER_PAIR to REMOVE, DELETED_PAIR to REMOVE), getQueuedOperations().toSet())
    }

    @Test
    fun removeLeftoverDeletedPacks() {
        directories.getPackDirectory(PAIR).mkdirs()
        directories.getOldDirectory(DELETED_PAIR).mkdirs()

        scheduler.removeLeftoverOldDirectories()

        assertEquals(listOf(DELETED_PAIR to REMOVE_OLD), getQueuedOperations())
    }

    @Test
    fun queueACorruptionCheckAfterAnUnfinishedDownloadOfThePair() = runTest {
        scheduler.download(PAIR)

        scheduler.removeIfCorrupted(PAIR)

        assertEquals(listOf(BLOCKED), getActiveWork(PAIR).filterNot { it.isDownload() }.map { it.state })
        assertEquals(listOf(PAIR to REMOVE_IF_CORRUPTED), getQueuedOperations())
    }

    private fun getActiveWork(pair: LanguagePair): List<WorkInfo> = getAllWork(pair).filterNot { it.state.isFinished }

    private fun getAllWork(pair: LanguagePair): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("$WORK_NAME_PREFIX${pair.key}").get()

    private fun WorkInfo.isDownload() = LanguagePackDownloadWorker::class.java.name in tags

    private fun getQueuedOperations(): List<Pair<LanguagePair?, LanguagePackFileOperation>> =
        getEnqueuedRequests()
            .filter { it.workSpec.workerClassName == LanguagePackFileWorker::class.java.name }
            .map { request ->
                val input = request.workSpec.input
                LanguagePair.fromKey(input.getString(PAIR_KEY)!!) to LanguagePackFileOperation.valueOf(input.getString(OPERATION_KEY)!!)
            }

    private fun getEnqueuedRequests(): List<OneTimeWorkRequest> {
        val requests = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager, atLeast(0)).enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), requests.capture())
        return requests.allValues
    }

    private class RunningWorker(context: Context, params: WorkerParameters, private val result: Deferred<Result>) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = result.await()

        // below Android 12 an expedited job asks for its notification before it starts
        override suspend fun getForegroundInfo() = ForegroundInfo(1, Notification())
    }

    private class FinishingWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()

        override fun getForegroundInfo() = ForegroundInfo(1, Notification())
    }

    private val workerFactory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            if (isWorkFinishing) FinishingWorker(appContext, workerParameters) else RunningWorker(appContext, workerParameters, runningWorkResult)
    }

    private companion object {
        val PAIR = LanguagePair("fr", "en")
        val OTHER_PAIR = LanguagePair("de", "en")
        val STAGED_PAIR = LanguagePair("es", "en")
        val DELETED_PAIR = LanguagePair("it", "en")
        const val WORK_NAME_PREFIX = "pageTranslationLanguagePack-"
    }
}
