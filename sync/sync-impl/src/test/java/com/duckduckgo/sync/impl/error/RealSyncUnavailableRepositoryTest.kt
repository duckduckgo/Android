/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.error

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.TestSharedPrefsProvider
import com.duckduckgo.sync.impl.engine.FakeNotificationBuilder
import com.duckduckgo.sync.impl.error.RealSyncUnavailableRepository.Companion.SYNC_ERROR_NOTIFICATION_ID
import com.duckduckgo.sync.impl.error.SchedulableErrorNotificationWorker.Companion.SYNC_ERROR_NOTIFICATION_TAG
import com.duckduckgo.sync.store.SyncUnavailableSharedPrefsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class RealSyncUnavailableRepositoryTest {

    @JvmField @Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(context)
    private val syncNotificationBuilder = FakeNotificationBuilder()
    private val syncUnavailableStore = SyncUnavailableSharedPrefsStore(
        sharedPrefsProv = TestSharedPrefsProvider(context),
    )
    private lateinit var workManager: WorkManager
    private lateinit var testee: RealSyncUnavailableRepository

    @Before
    fun setup() {
        initializeWorkManager()
        workManager = WorkManager.getInstance(context)
        testee = RealSyncUnavailableRepository(
            context,
            syncUnavailableStore,
            notificationManager,
            syncNotificationBuilder,
            workManager,
        )
    }

    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    @Test
    fun whenServerBecomesAvailableThenSyncAvailable() {
        syncUnavailableStore.isSyncUnavailable = true
        testee.onServerAvailable()
        assertFalse(syncUnavailableStore.isSyncUnavailable)
        assertEquals("", syncUnavailableStore.syncUnavailableSince)
    }

    @Test
    fun whenServerUnavailableThenSyncUnavailable() {
        testee.onServerUnavailable()
        assertTrue(syncUnavailableStore.isSyncUnavailable)
    }

    @Test
    fun whenServerUnavailableThenUpdateTimestampOnce() {
        testee.onServerUnavailable()
        val unavailableSince = syncUnavailableStore.syncUnavailableSince
        assertTrue(unavailableSince.isNotEmpty())
        testee.onServerUnavailable()
        assertEquals(unavailableSince, syncUnavailableStore.syncUnavailableSince)
    }

    @Test
    fun whenServerUnavailableThenUpdateCounter() {
        testee.onServerUnavailable()
        assertTrue(syncUnavailableStore.isSyncUnavailable)
        assertEquals(1, syncUnavailableStore.syncErrorCount)
        testee.onServerUnavailable()
        testee.onServerUnavailable()
        testee.onServerUnavailable()
        assertEquals(4, syncUnavailableStore.syncErrorCount)
    }

    @Test
    fun whenServerUnavailableThenScheduleNotification() {
        testee.onServerUnavailable()
        assertTrue(syncUnavailableStore.isSyncUnavailable)
        val syncErrorNotification = workManager.getWorkInfosByTag(SYNC_ERROR_NOTIFICATION_TAG).get()
        assertEquals(1, syncErrorNotification.size)
    }

    @Test
    fun whenErrorCounterReachesThresholdThenTriggerNotification() {
        syncUnavailableStore.syncErrorCount = RealSyncUnavailableRepository.ERROR_THRESHOLD_NOTIFICATION_COUNT
        testee.onServerUnavailable()
        notificationManager.activeNotifications
            .find { it.id == SYNC_ERROR_NOTIFICATION_ID } ?: fail("Notification not found")
    }

    @Test
    fun whenUserNotifiedTodayThenDoNotTriggerNotification() {
        syncUnavailableStore.userNotifiedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        testee.triggerNotification()
        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_ERROR_NOTIFICATION_ID })
    }

    @Test
    fun whenUserNotifiedYesterdayThenTriggerNotificationAndUpdateNotificationTimestamp() {
        syncUnavailableStore.userNotifiedAt = OffsetDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        testee.triggerNotification()
        notificationManager.activeNotifications
            .find { it.id == SYNC_ERROR_NOTIFICATION_ID } ?: fail("Notification not found")

        val today = LocalDateTime.now().toLocalDate()
        val lastNotification = LocalDateTime.parse(syncUnavailableStore.userNotifiedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        assertEquals(today, lastNotification)
    }

    @Test
    fun whenServerAvailableThenClearAnyScheduledWorkerNotification() {
        testee.onServerUnavailable()
        val syncErrorNotification = workManager.getWorkInfosByTag(SYNC_ERROR_NOTIFICATION_TAG).get()
        assertTrue(syncErrorNotification.first().state == State.ENQUEUED)
        testee.onServerAvailable()
        val syncErrorNotificationAfterSuccess = workManager.getWorkInfosByTag(SYNC_ERROR_NOTIFICATION_TAG).get()
        assertTrue(syncErrorNotificationAfterSuccess.first().state == State.CANCELLED)
    }

    @Test
    fun whenServerAvailableThenClearNotification() {
        syncUnavailableStore.syncErrorCount = RealSyncUnavailableRepository.ERROR_THRESHOLD_NOTIFICATION_COUNT
        testee.onServerUnavailable()
        notificationManager.activeNotifications
            .find { it.id == SYNC_ERROR_NOTIFICATION_ID } ?: fail("Notification not found")
        testee.onServerAvailable()
        assertNull(notificationManager.activeNotifications.find { it.id == SYNC_ERROR_NOTIFICATION_ID })
    }

    private fun initializeWorkManager() {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(testWorkerFactory())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun testWorkerFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                return SchedulableErrorNotificationWorker(appContext, workerParameters).also {
                    it.syncPausedRepository = testee
                }
            }
        }
    }
}
