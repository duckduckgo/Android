/*
 * Copyright (c) 2025 DuckDuckGo
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

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class DefaultFileDownloadNotificationManagerTest {

    private val mockNotificationManager: NotificationManagerCompat = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockRetryUrlStore: FailedDownloadRetryUrlStore = mock()

    private lateinit var notificationManager: DefaultFileDownloadNotificationManager

    @Before
    fun setup() {
        val realContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = spy(realContext)
        doReturn("stub").whenever(context).getString(any())

        notificationManager = DefaultFileDownloadNotificationManager(
            notificationManager = mockNotificationManager,
            applicationContext = context,
            appBuildConfig = mockAppBuildConfig,
            failedDownloadRetryUrlStore = mockRetryUrlStore,
        )
    }

    @Test
    fun whenShowDownloadFailedWithUrlThenUrlSavedToStore() {
        val downloadId = 1L
        val url = "https://example.com/file.txt"

        notificationManager.showDownloadFailedNotification(downloadId, url)

        verify(mockRetryUrlStore).saveRetryUrl(downloadId, url)
    }

    @Test
    fun whenShowDownloadFailedWithNullUrlThenUrlNotSavedToStore() {
        val downloadId = 1L

        notificationManager.showDownloadFailedNotification(downloadId, null)

        verifyNoInteractions(mockRetryUrlStore)
    }

    @Test
    fun whenShowDownloadFailedWithDataUriThenUrlSavedToStore() {
        val downloadId = 1L
        val dataUri = "data:image/png;base64," + "A".repeat(10_000)

        notificationManager.showDownloadFailedNotification(downloadId, dataUri)

        verify(mockRetryUrlStore).saveRetryUrl(downloadId, dataUri)
    }

    @Test
    fun whenCancelNotificationThenNotificationCancelled() {
        val downloadId = 1L

        notificationManager.cancelDownloadFileNotification(downloadId)

        verify(mockNotificationManager).cancel(downloadId.toInt())
    }
}
