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

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.FileDownloadNotificationManager
import com.duckduckgo.downloads.api.FileDownloader
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class FileDownloadNotificationActionReceiverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockFileDownloader: FileDownloader = mock()
    private val mockFileDownloadNotificationManager: FileDownloadNotificationManager = mock()
    private val mockDownloadsRepository: DownloadsRepository = mock()
    private val mockPixel: Pixel = mock()
    private val mockFailedDownloadRetryUrlStore: FailedDownloadRetryUrlStore = mock()
    private val mockNewDownloadState: InternalNewDownloadState = mock()

    private val testee =
        FileDownloadNotificationActionReceiver(
            context = context,
            fileDownloader = mockFileDownloader,
            fileDownloadNotificationManager = mockFileDownloadNotificationManager,
            downloadsRepository = mockDownloadsRepository,
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pixel = mockPixel,
            failedDownloadRetryUrlStore = mockFailedDownloadRetryUrlStore,
            newDownloadState = mockNewDownloadState,
        )

    @Test
    fun whenDownloadSeenIntentReceivedThenNewDownloadIsAcknowledged() = runTest {
        testee.onReceive(context, FileDownloadNotificationActionReceiver.downloadSeenIntent(context, DOWNLOAD_ID))

        verify(mockNewDownloadState).onNewDownloadAcknowledged()
    }

    @Test
    fun whenDismissIntentReceivedThenNewDownloadIsNotAcknowledged() = runTest {
        testee.onReceive(context, FileDownloadNotificationActionReceiver.dismissDownloadIntent(context, DOWNLOAD_ID))

        verify(mockNewDownloadState, never()).onNewDownloadAcknowledged()
    }

    companion object {
        private const val DOWNLOAD_ID = 1L
    }
}
