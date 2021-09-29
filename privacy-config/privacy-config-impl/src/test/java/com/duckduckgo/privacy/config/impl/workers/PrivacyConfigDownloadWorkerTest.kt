/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.impl.workers

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.privacy.config.impl.PrivacyConfigDownloader
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PrivacyConfigDownloadWorkerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: PrivacyConfigDownloadWorker
    private val mockPrivacyConfigDownloader: PrivacyConfigDownloader = mock()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock()
    }

    @Test
    fun whenDoWorkIfDownloadReturnsTrueThenReturnSuccess() = coroutineRule.runBlocking {

        whenever(mockPrivacyConfigDownloader.download()).thenReturn(true)

        val worker = TestListenableWorkerBuilder<PrivacyConfigDownloadWorker>(context = context).build()

        worker.privacyConfigDownloader = mockPrivacyConfigDownloader
        worker.dispatcherProvider = coroutineRule.testDispatcherProvider

        val result = worker.doWork()
        assertThat(result, `is`(ListenableWorker.Result.success()))
    }

    @Test
    fun whenDoWorkIfDownloadReturnsFalseThenReturnRetry() = coroutineRule.runBlocking {

        whenever(mockPrivacyConfigDownloader.download()).thenReturn(false)

        val worker = TestListenableWorkerBuilder<PrivacyConfigDownloadWorker>(context = context).build()

        worker.privacyConfigDownloader = mockPrivacyConfigDownloader
        worker.dispatcherProvider = coroutineRule.testDispatcherProvider

        val result = worker.doWork()
        assertThat(result, `is`(ListenableWorker.Result.retry()))
    }
}
