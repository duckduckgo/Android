/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl.pixels

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class AdClickDailyReportingWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAdClickPixels: AdClickPixels = mock()

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun whenDoWorkThenCallFireCountPixelWithCorrectParamNameAndReturnSuccess() =
        runTest {
            val worker = TestListenableWorkerBuilder<AdClickDailyReportingWorker>(context = context).build()
            worker.adClickPixels = mockAdClickPixels
            worker.dispatchers = coroutineRule.testDispatcherProvider

            val result = worker.doWork()

            verify(mockAdClickPixels).fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
            Assert.assertEquals(result, ListenableWorker.Result.success())
        }
}
