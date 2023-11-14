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

package com.duckduckgo.fingerprintprotection

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.fingerprintprotection.impl.FingerprintProtectionSeedWorker
import com.duckduckgo.fingerprintprotection.store.seed.FingerprintProtectionSeedRepository
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class FingerprintProtectionSeedWorkerTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockFingerprintProtectionSeedRepository: FingerprintProtectionSeedRepository = mock()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = mock()
    }

    @Test
    fun whenDoWorkThenReturnSuccess() = runTest {
        val worker = TestListenableWorkerBuilder<FingerprintProtectionSeedWorker>(context = context).build()

        worker.fingerprintProtectionSeedRepository = mockFingerprintProtectionSeedRepository
        worker.dispatcherProvider = coroutineRule.testDispatcherProvider

        val result = worker.doWork()
        assertThat(result, `is`(Result.success()))
        verify(mockFingerprintProtectionSeedRepository).storeNewSeed()
    }
}
