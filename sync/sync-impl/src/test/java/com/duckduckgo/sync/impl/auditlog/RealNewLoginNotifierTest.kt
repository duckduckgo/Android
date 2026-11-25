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

package com.duckduckgo.sync.impl.auditlog

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.sync.impl.auditlog.RealNewLoginNotifier.Companion.NEW_LOGIN_NOTIFICATION_ID
import com.duckduckgo.sync.impl.auditlog.RealNewLoginNotifier.Companion.SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogActivity
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogScreenParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RealNewLoginNotifierTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(context)
    private val stateStore = FakeStateStore()
    private val feature = FakeLoginAuditLogFeature()
    private val deviceActivityIntent = Intent(context, SyncAuditLogActivity::class.java)
    private val globalActivityStarter: GlobalActivityStarter = mock<GlobalActivityStarter>().also {
        whenever(it.startIntent(any(), eq(SyncAuditLogScreenParams(openedFromNotification = true)))).thenReturn(deviceActivityIntent)
    }

    private val notifier = RealNewLoginNotifier(
        context = context,
        notificationManager = notificationManager,
        stateStore = stateStore,
        loginAuditLogFeature = feature,
        globalActivityStarter = globalActivityStarter,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(POST_NOTIFICATIONS)
    }

    @Test
    fun whenCheckAddsOneLoginThenPostsSingularNotification() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        val notification = postedNotification()
        assertNotNull(notification)
        assertEquals("New synced device", notification!!.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("This phone has synced with another device", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID, notification.channelId)
    }

    @Test
    fun whenNotificationIsTappedThenOnlyDeviceActivityOpensOnTopOfTheCurrentScreen() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        val openedIntents = shadowOf(postedNotification()!!.contentIntent).savedIntents.toList()
        assertEquals(1, openedIntents.size)
        assertTrue(openedIntents.single().filterEquals(deviceActivityIntent))
    }

    @Test
    fun whenNotificationIsTappedThenItIsRemoved() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertTrue(postedNotification()!!.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun whenNotificationIsShowingAndAnotherLoginIsFoundThenItIsReplacedWithThePluralText() = runTest {
        stateStore.givenPending(pendingLogin(1))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        stateStore.givenPending(pendingLogin(1), pendingLogin(2))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertEquals(1, notificationManager.activeNotifications.size)
        val notification = postedNotification()!!
        assertEquals("New synced devices", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("This phone has synced with 2 devices", notification.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun whenOneDeviceLoggedInTwiceThenItCountsOnce() = runTest {
        stateStore.givenPending(pendingLogin(1, name = "Laptop"), pendingLogin(2, name = "Laptop"))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 2, renamedLogins = 0))

        assertEquals("This phone has synced with another device", postedNotification()!!.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun whenNotificationIsPostedForANewLoginThenItAlertsAgain() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertFalse(postedNotification()!!.alertsOnlyOnce())
    }

    @Test
    fun whenALateNameArrivesWhileShowingThenTextIsUpdatedWithoutAlerting() = runTest {
        stateStore.givenPending(pendingLogin(1, name = "Unknown device"), pendingLogin(2, name = "Unknown device"))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 2, renamedLogins = 0))

        stateStore.givenPending(pendingLogin(1, name = "Unknown device"), pendingLogin(2, name = "Laptop"))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 1))

        val notification = postedNotification()!!
        assertEquals("This phone has synced with 2 devices", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(notification.alertsOnlyOnce())
    }

    @Test
    fun whenALateNameArrivesWhileNotShowingThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 1))

        assertNull(postedNotification())
    }

    @Test
    fun whenCheckFindsNothingNewThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenCheckDidNotRunThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))

        listOf(
            AuditLogCheckResult.Disabled,
            AuditLogCheckResult.NotSignedIn,
            AuditLogCheckResult.AlreadyRunning,
            AuditLogCheckResult.Initialised,
            AuditLogCheckResult.Discarded,
            AuditLogCheckResult.Failed(AuditLogCheckResult.FailureReason.NETWORK),
        ).forEach { notifier.onCheckFinished(it) }

        assertNull(postedNotification())
    }

    @Test
    fun whenNotificationFlagIsOffThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))
        feature.notificationEnabled = false

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenPendingListIsEmptyThenDoesNotPost() = runTest {
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenNotificationsAreTurnedOffThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenChannelIsBlockedThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(SYNC_NEW_LOGIN_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_NONE)
                .setName("blocked")
                .build(),
        )

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenPermissionIsMissingThenDoesNotPost() = runTest {
        stateStore.givenPending(pendingLogin(1))
        shadowOf(context as Application).denyPermissions(POST_NOTIFICATIONS)

        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        assertNull(postedNotification())
    }

    @Test
    fun whenCancelledThenNotificationIsRemoved() = runTest {
        stateStore.givenPending(pendingLogin(1))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        notifier.cancel()

        assertNull(postedNotification())
    }

    @Test
    fun whenSyncIsDisabledThenNotificationIsRemoved() = runTest {
        stateStore.givenPending(pendingLogin(1))
        notifier.onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))

        notifier.onSyncDisabled()

        assertNull(postedNotification())
    }

    private fun postedNotification(): Notification? =
        notificationManager.activeNotifications.find { it.id == NEW_LOGIN_NOTIFICATION_ID }?.notification

    private fun Notification.alertsOnlyOnce() = flags and Notification.FLAG_ONLY_ALERT_ONCE != 0

    private fun pendingLogin(n: Int, name: String = "Device $n") = PendingLogin(
        entryId = "entry-$n",
        deviceId = "device-$n",
        deviceName = name,
        occurredAt = Instant.parse("2026-11-23T10:00:00Z").plusSeconds(n.toLong()),
    )

    private class FakeLoginAuditLogFeature : LoginAuditLogFeature {
        var notificationEnabled = true

        override suspend fun isAuditLogEnabled(): Boolean = true

        override suspend fun isNotificationEnabled(): Boolean = notificationEnabled
    }

    private class FakeStateStore : AuditLogCheckStateStore {
        private val state = MutableStateFlow(AuditLogCheckState(cursor = "cursor"))

        fun givenPending(vararg logins: PendingLogin) {
            state.value = state.value.copy(pendingLogins = logins.toList())
        }

        override suspend fun read(): AuditLogCheckState = state.value

        override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) {
            transform(state.value)?.let { state.value = it }
        }

        override fun pendingLogins(): Flow<List<PendingLogin>> = state.map { it.pendingLogins }
    }
}
