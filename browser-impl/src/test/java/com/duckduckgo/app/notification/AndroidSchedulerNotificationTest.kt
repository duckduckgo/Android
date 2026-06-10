/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.notification

/*
 * Copyright (c) 2019 DuckDuckGo
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

import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.notification.model.SchedulableNotification
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidNotificationSchedulerTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockClearNotification: SchedulableNotification = mock()
    private val mockPrivacyNotification: SchedulableNotification = mock()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workManager: WorkManager
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var testee: NotificationScheduler

    @Before
    fun before() {
        initializeWorkManager()
        notificationManager = NotificationManagerCompat.from(context)

        testee = NotificationScheduler(
            workManager,
            notificationManager,
            mockClearNotification,
            mockPrivacyNotification,
        )
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
    fun whenPrivacyNotificationClearDataCanShowThenPrivacyNotificationIsScheduled() = runTest {
        whenever(mockPrivacyNotification.canShow()).thenReturn(true)
        whenever(mockClearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.javaObjectType.name)
    }

    @Test
    fun whenPrivacyNotificationCanShowButClearDataCannotThenPrivacyNotificationIsScheduled() = runTest {
        whenever(mockPrivacyNotification.canShow()).thenReturn(true)
        whenever(mockClearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNotificationScheduled(PrivacyNotificationWorker::class.javaObjectType.name)
    }

    @Test
    fun whenPrivacyNotificationCannotShowAndClearNotificationCanShowThenClearNotificationIsScheduled() = runTest {
        whenever(mockPrivacyNotification.canShow()).thenReturn(false)
        whenever(mockClearNotification.canShow()).thenReturn(true)

        testee.scheduleNextNotification()

        assertNotificationScheduled(ClearDataNotificationWorker::class.javaObjectType.name)
    }

    @Test
    fun whenPrivacyNotificationAndClearNotificationCannotShowThenNoNotificationScheduled() = runTest {
        whenever(mockPrivacyNotification.canShow()).thenReturn(false)
        whenever(mockClearNotification.canShow()).thenReturn(false)

        testee.scheduleNextNotification()

        assertNoNotificationScheduled()
    }

    private fun assertNotificationScheduled(
        workerName: String?,
        tag: String = NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG,
    ) {
        assertTrue(
            getScheduledWorkers(tag).any {
                it.tags.contains(workerName)
            },
        )
    }

    private fun assertNotificationNotScheduled(
        workerName: String?,
        tag: String = NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG,
    ) {
        assertFalse(
            getScheduledWorkers(tag).any {
                it.tags.contains(workerName)
            },
        )
    }

    private fun assertNoNotificationScheduled(tag: String = NotificationScheduler.UNUSED_APP_WORK_REQUEST_TAG) {
        assertTrue(getScheduledWorkers(tag).isEmpty())
    }

    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }
}
