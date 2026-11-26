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
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationPermission.Step.NONE
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationPermission.Step.OPEN_SETTINGS
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationPermission.Step.REQUEST_PERMISSION
import com.duckduckgo.sync.impl.auditlog.RealNewLoginNotificationPermission.Companion.NEW_LOGIN_CHANNEL_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RealNewLoginNotificationPermissionTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = NotificationManagerCompat.from(context)

    private val permission = RealNewLoginNotificationPermission(
        context = context,
        notificationManager = notificationManager,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setUp() {
        givenPermissionGranted(true)
    }

    @Test
    fun whenPermissionGrantedAndNotificationsOnThenCanPost() = runTest {
        assertTrue(permission.canPost())
    }

    @Test
    fun whenChannelNotCreatedYetThenCanPost() = runTest {
        assertTrue(permission.canPost())
    }

    @Test
    fun whenChannelOnThenCanPost() = runTest {
        givenChannel(NotificationManagerCompat.IMPORTANCE_HIGH)

        assertTrue(permission.canPost())
    }

    @Test
    fun whenPermissionNotGrantedThenCannotPost() = runTest {
        givenPermissionGranted(false)

        assertFalse(permission.canPost())
    }

    @Test
    fun whenAppNotificationsOffThenCannotPost() = runTest {
        givenAppNotificationsEnabled(false)

        assertFalse(permission.canPost())
    }

    @Test
    fun whenChannelOffThenCannotPost() = runTest {
        givenChannel(NotificationManagerCompat.IMPORTANCE_NONE)

        assertFalse(permission.canPost())
    }

    @Test
    @Config(sdk = [28])
    fun whenAndroid12OrLowerAndNotificationsOnThenCanPostWithoutRuntimePermission() = runTest {
        givenPermissionGranted(false)

        assertTrue(permission.canPost())
    }

    @Test
    fun whenCanPostThenNoStep() = runTest {
        assertEquals(NONE, permission.nextStep(shouldShowRationale = false))
    }

    @Test
    fun whenPermissionRefusedOnceThenRequestPermissionAgain() = runTest {
        givenPermissionGranted(false)

        assertEquals(REQUEST_PERMISSION, permission.nextStep(shouldShowRationale = true))
    }

    @Test
    fun whenPermissionMissingAndNoRationaleThenOpenSettings() = runTest {
        givenPermissionGranted(false)

        assertEquals(OPEN_SETTINGS, permission.nextStep(shouldShowRationale = false))
    }

    @Test
    fun whenPermissionGrantedButAppNotificationsOffThenOpenSettings() = runTest {
        givenAppNotificationsEnabled(false)

        assertEquals(OPEN_SETTINGS, permission.nextStep(shouldShowRationale = false))
    }

    @Test
    fun whenChannelOffThenOpenSettings() = runTest {
        givenChannel(NotificationManagerCompat.IMPORTANCE_NONE)

        assertEquals(OPEN_SETTINGS, permission.nextStep(shouldShowRationale = true))
    }

    @Test
    @Config(sdk = [28])
    fun whenAndroid12OrLowerAndNotificationsOffThenOpenSettings() = runTest {
        givenAppNotificationsEnabled(false)

        assertEquals(OPEN_SETTINGS, permission.nextStep(shouldShowRationale = true))
    }

    @Test
    fun whenAppNotificationsOffThenSettingsIntentOpensAppNotificationSettings() = runTest {
        givenAppNotificationsEnabled(false)
        givenChannel(NotificationManagerCompat.IMPORTANCE_NONE)

        val intent = permission.settingsIntent()

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun whenPermissionNotGrantedThenSettingsIntentOpensAppNotificationSettings() = runTest {
        givenPermissionGranted(false)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, permission.settingsIntent().action)
    }

    @Test
    fun whenOnlyChannelOffThenSettingsIntentOpensChannelSettings() = runTest {
        givenChannel(NotificationManagerCompat.IMPORTANCE_NONE)

        val intent = permission.settingsIntent()

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(NEW_LOGIN_CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    private fun givenPermissionGranted(granted: Boolean) {
        val app = shadowOf(context as Application)
        if (granted) app.grantPermissions(POST_NOTIFICATIONS) else app.denyPermissions(POST_NOTIFICATIONS)
    }

    private fun givenAppNotificationsEnabled(enabled: Boolean) {
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(systemManager).setNotificationsEnabled(enabled)
    }

    private fun givenChannel(importance: Int) {
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(NEW_LOGIN_CHANNEL_ID, importance).setName("New login").build(),
        )
    }
}
