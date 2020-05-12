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
import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.job.JobCleaner.Companion.allDeprecatedNotificationWorkTags
import com.duckduckgo.app.notification.NotificationScheduler
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JobCleanerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var testee: JobCleaner

    @Before
    fun before() {
        initializeWorkManager()
        workManager = WorkManager.getInstance(context)
        testee = AndroidJobCleaner(workManager)
    }

    @Test
    fun whenStartedThenAllDeprecatedWorkIsCancelled() {
        allDeprecatedNotificationWorkTags().forEach {
            val requestBuilder = OneTimeWorkRequestBuilder<NotificationScheduler.PrivacyNotificationWorker>()
            val request = requestBuilder
                .addTag("tag")
                .build()
            workManager.enqueue(request)
        }

        testee.cleanDeprecatedJobs()

        allDeprecatedNotificationWorkTags().forEach {
            assertTrue(getScheduledWorkers(it).isEmpty())
        }
    }

    // https://developer.android.com/topic/libraries/architecture/workmanager/how-to/integration-testing
    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}