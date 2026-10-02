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

package com.duckduckgo.downloads.impl

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.Application
import android.app.Notification
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(AndroidJUnit4::class)
class DefaultFileDownloadNotificationManagerTest {

    private val mockNotificationManager: NotificationManagerCompat = mock()

    private lateinit var notificationManager: DefaultFileDownloadNotificationManager

    @Before
    fun setup() {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = spy(realContext)
        doReturn("stub").whenever(context).getString(any())

        notificationManager = DefaultFileDownloadNotificationManager(
            notificationManager = mockNotificationManager,
            applicationContext = context,
        )
    }

    @Test
    fun whenCancelNotificationThenNotificationCancelled() {
        val downloadId = 1L

        notificationManager.cancelDownloadFileNotification(downloadId)

        verify(mockNotificationManager).cancel(downloadId.toInt())
    }

    @Test
    fun whenDownloadFinishedNotificationShownThenDeleteIntentIsDownloadSeenIntent() {
        val downloadId = 1L
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)

        notificationManager.showDownloadFinishedNotification(downloadId, File("test.txt"), null)

        val captor = argumentCaptor<Notification>()
        verify(mockNotificationManager).notify(eq(downloadId.toInt()), captor.capture())
        val savedIntent = shadowOf(captor.firstValue.deleteIntent).savedIntent
        assertEquals(downloadId, savedIntent.getLongExtra("downloadId", -1))
        assertEquals("EXTRA_DOWNLOAD_SEEN", savedIntent.getStringExtra("CTA"))
    }

    @Test
    fun whenDownloadFinishedNotificationShownThenDeleteIntentRequestCodeDiffersFromFailedNotificationDeleteIntent() {
        val downloadId = 0L
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        shadowOf(application).grantPermissions(POST_NOTIFICATIONS)

        notificationManager.showDownloadFinishedNotification(downloadId, File("test.txt"), null)
        val finishedCaptor = argumentCaptor<Notification>()
        verify(mockNotificationManager).notify(eq(downloadId.toInt()), finishedCaptor.capture())
        val finishedDeleteRequestCode = shadowOf(finishedCaptor.firstValue.deleteIntent).requestCode

        notificationManager.showDownloadFailedNotification(downloadId, null)
        val failedCaptor = argumentCaptor<Notification>()
        verify(mockNotificationManager, times(2)).notify(eq(downloadId.toInt()), failedCaptor.capture())
        val failedDeleteRequestCode = shadowOf(failedCaptor.lastValue.deleteIntent).requestCode

        assertNotEquals(downloadId.toInt(), finishedDeleteRequestCode)
        assertNotEquals(downloadId.toInt().inv(), finishedDeleteRequestCode)
        assertNotEquals(failedDeleteRequestCode, finishedDeleteRequestCode)
    }
}
