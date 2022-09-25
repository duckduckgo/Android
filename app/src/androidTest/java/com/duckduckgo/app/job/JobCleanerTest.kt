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

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.job.JobCleaner.Companion.allDeprecatedNotificationWorkTags
import com.duckduckgo.app.notification.NotificationScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class JobCleanerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var testee: JobCleaner

    @Before
    fun before() {
        initializeWorkManager()
        testee = AndroidJobCleaner(workManager)
    }

    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing
    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun whenStartedThenAllDeprecatedWorkIsCancelled() {
        enqueueDeprecatedWorkers()
        assertDeprecatedWorkersAreEnqueued()
        testee.cleanDeprecatedJobs()
        assertDeprecatedWorkersAreNotEnqueued()
    }

    @Test
    fun whenStartedAndNoDeprecatedJobsAreScheduledThenNothingIsRemoved() {
        enqueueNonDeprecatedWorkers()
        assertNonDeprecatedWorkersAreEnqueued()
        testee.cleanDeprecatedJobs()
        assertNonDeprecatedWorkersAreEnqueued()
    }

    private fun enqueueDeprecatedWorkers() {
        allDeprecatedNotificationWorkTags().forEach {
            val requestBuilder = OneTimeWorkRequestBuilder<TestWorker>()
            val request = requestBuilder
                .addTag(it)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            workManager.enqueue(request)
        }
    }

    private fun enqueueNonDeprecatedWorkers() {
        allDeprecatedNotificationWorkTags().forEach {
            val requestBuilder = OneTimeWorkRequestBuilder<TestWorker>()
            val request = requestBuilder
                .addTag(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG)
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            workManager.enqueue(request)
        }
    }

    private fun assertDeprecatedWorkersAreEnqueued() {
        allDeprecatedNotificationWorkTags().forEach {
            val scheduledWorkers = getScheduledWorkers(it)
            assertFalse(scheduledWorkers.isEmpty())
        }
    }

    private fun assertDeprecatedWorkersAreNotEnqueued() {
        allDeprecatedNotificationWorkTags().forEach {
            val scheduledWorkers = getScheduledWorkers(it)
            assertTrue(scheduledWorkers.isEmpty())
        }
    }

    private fun assertNonDeprecatedWorkersAreEnqueued() {
        val scheduledWorkers = getScheduledWorkers(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG)
        assertFalse(scheduledWorkers.isEmpty())
    }

    private fun assertNonDeprecatedWorkersAreNotEnqueued() {
        val scheduledWorkers = getScheduledWorkers(NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG)
        assertTrue(scheduledWorkers.isEmpty())
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}
