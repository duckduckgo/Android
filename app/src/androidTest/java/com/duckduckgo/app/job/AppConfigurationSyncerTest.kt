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

package com.duckduckgo.app.job

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder
import com.duckduckgo.app.global.job.AppConfigurationSyncWorkRequestBuilder.Companion.APP_CONFIG_SYNC_WORK_TAG
import com.duckduckgo.app.global.job.AppConfigurationWorker
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import io.reactivex.Completable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppConfigurationSyncerTest {

    private lateinit var testee: AppConfigurationSyncer
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private val mockDownloader: ConfigurationDownloader = mock()

    @Before
    fun setup() {
        initializeWorkManager()
        whenever(mockDownloader.downloadTask()).thenReturn(Completable.complete())
        testee = AppConfigurationSyncer(AppConfigurationSyncWorkRequestBuilder(), workManager, mockDownloader)
    }

    @Test
    fun whenInitializedThenNoWorkScheduled() {
        assertTrue(workManager.getSyncWork().isEmpty())
    }

    @Test
    fun whenSyncScheduledButNotYetRunThenWorkEnqueued() {
        testee.scheduleRegularSync()

        val workInfos = workManager.getSyncWork()
        assertWorkIsEnqueuedStatus(workInfos)
    }

    @Test
    fun whenSyncFinishedThenWorkStillScheduled() {
        testee.scheduleRegularSync()

        executeWorker()

        val workInfos = workManager.getSyncWork()
        assertWorkIsEnqueuedStatus(workInfos)
    }

    private fun executeWorker() {
        WorkManagerTestInitHelper.getTestDriver(context)?.setAllConstraintsMet(workManager.getSyncWork().first().id)
    }

    private fun assertWorkIsEnqueuedStatus(workInfos: List<WorkInfo>) {
        assertSingleInstanceOfWork(workInfos)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
    }

    private fun assertSingleInstanceOfWork(workInfos: List<WorkInfo>) {
        assertEquals(1, workInfos.size)
    }

    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(testWorkerFactory())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    private fun testWorkerFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                return AppConfigurationWorker(appContext, workerParameters).also {
                    it.appConfigurationDownloader = mockDownloader
                }
            }
        }
    }

    private fun WorkManager.getSyncWork(): List<WorkInfo> {
        return getWorkInfosByTag(APP_CONFIG_SYNC_WORK_TAG).get()
    }
}
