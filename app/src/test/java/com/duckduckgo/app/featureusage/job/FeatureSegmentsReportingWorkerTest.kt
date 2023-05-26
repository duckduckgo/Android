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

package com.duckduckgo.app.featureusage.job

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.featureusage.FeatureSegmentsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
internal class FeatureSegmentsReportingWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockFeatureSegmentsManager: FeatureSegmentsManager = mock()
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mock()
    }

    @Test
    fun givenUserWithFeatureSegmentsWhenDoWorkThenCallFirePixelAndReturnSuccess() =
        runTest {
            whenever(mockFeatureSegmentsManager.shouldFireSegmentsPixel()).thenReturn(true)
            val worker = TestListenableWorkerBuilder<FeatureSegmentsReportingWorker>(context = context).build()
            worker.featureSegmentsManager = mockFeatureSegmentsManager
            worker.dispatchers = coroutineRule.testDispatcherProvider

            val result = worker.doWork()

            verify(mockFeatureSegmentsManager).fireFeatureSegmentsPixel()
            assertEquals(result, ListenableWorker.Result.success())
        }

    @Test
    fun givenUserWithNoFeatureSegmentsWhenDoWorkThenDontCallFirePixelAndReturnSuccess() =
        runTest {
            whenever(mockFeatureSegmentsManager.shouldFireSegmentsPixel()).thenReturn(false)
            val worker = TestListenableWorkerBuilder<FeatureSegmentsReportingWorker>(context = context).build()
            worker.featureSegmentsManager = mockFeatureSegmentsManager
            worker.dispatchers = coroutineRule.testDispatcherProvider

            val result = worker.doWork()

            verify(mockFeatureSegmentsManager, never()).fireFeatureSegmentsPixel()
            assertEquals(result, ListenableWorker.Result.success())
        }
}
