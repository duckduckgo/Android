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
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LanguagePackSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java)
    private var isDeviceSupported = true
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: RealLanguagePackScheduler

    @Before
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(DownloadingWorkerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        feature.translation().setRawStoredState(State(enable = true))
        val deviceSupport = object : PageTranslationDeviceSupport {
            override fun isSupported() = isDeviceSupported
        }
        scheduler = RealLanguagePackScheduler(workManager, feature, deviceSupport)
    }

    @Test
    fun joinADownloadOfTheSamePairThatIsRunning() {
        scheduler.download(PAIR)
        val running = getActiveDownloadIds(PAIR).single()

        scheduler.download(PAIR)

        assertEquals(listOf(running), getActiveDownloadIds(PAIR))
    }

    @Test
    fun replaceTheDownloadOnRetry() {
        scheduler.download(PAIR)
        val stopped = getActiveDownloadIds(PAIR).single()

        scheduler.retry(PAIR)

        val replacement = getActiveDownloadIds(PAIR).single()
        assertNotEquals(stopped, replacement)
    }

    @Test
    fun runDownloadsOfDifferentPairsSeparately() {
        scheduler.download(PAIR)
        scheduler.download(OTHER_PAIR)

        assertEquals(1, getActiveDownloadIds(PAIR).size)
        assertEquals(1, getActiveDownloadIds(OTHER_PAIR).size)
    }

    @Test
    fun queueNothingWhenTranslationIsOff() {
        feature.translation().setRawStoredState(State(enable = false))

        scheduler.download(PAIR)
        scheduler.retry(PAIR)

        assertEquals(emptyList<UUID>(), getActiveDownloadIds(PAIR))
    }

    @Test
    fun queueNothingOnUnsupportedDevices() {
        isDeviceSupported = false

        scheduler.download(PAIR)
        scheduler.retry(PAIR)

        assertEquals(emptyList<UUID>(), getActiveDownloadIds(PAIR))
    }

    private fun getActiveDownloadIds(pair: String): List<UUID> =
        workManager.getWorkInfosForUniqueWork("$WORK_NAME_PREFIX$pair").get().filterNot { it.state.isFinished }.map { it.id }

    private class DownloadingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = awaitCancellation()

        // below Android 12 an expedited job asks for its notification before it starts
        override suspend fun getForegroundInfo() = ForegroundInfo(1, Notification())
    }

    private object DownloadingWorkerFactory : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            DownloadingWorker(appContext, workerParameters)
    }

    private companion object {
        const val PAIR = "fr-en"
        const val OTHER_PAIR = "de-en"
        const val WORK_NAME_PREFIX = "pageTranslationLanguagePack-"
    }
}
