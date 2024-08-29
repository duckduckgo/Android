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

package com.duckduckgo.app.browser.httperrors

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class HttpErrorDailyReportingWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockHttpErrorPixels: HttpErrorPixels = mock()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun whenDoWorkThenCallFireCountPixelWithCorrectParamNameAndReturnSuccess() =
        runTest {
            val worker = TestListenableWorkerBuilder<HttpErrorDailyReportingWorker>(context = context).build()
            worker.httpErrorPixels = mockHttpErrorPixels
            worker.dispatchers = coroutineRule.testDispatcherProvider

            val result = worker.doWork()

            verify(mockHttpErrorPixels).fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
            verify(mockHttpErrorPixels).fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_4XX_DAILY)
            verify(mockHttpErrorPixels).fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY)
            assertEquals(result, ListenableWorker.Result.success())
        }
}
