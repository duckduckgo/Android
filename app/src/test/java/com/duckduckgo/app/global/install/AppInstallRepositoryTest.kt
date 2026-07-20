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

package com.duckduckgo.app.global.install

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class AppInstallRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val appInstallStore: AppInstallStore = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()

    private val testee = AppInstallRepository(
        appInstallStore = appInstallStore,
        currentTimeProvider = currentTimeProvider,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenInstallTimestampNotRecordedThenInstallAgeIsNull() = runTest {
        whenever(appInstallStore.installTimestamp).thenReturn(0L)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW)

        assertNull(testee.getInstallAge())
    }

    @Test
    fun whenInstallTimestampIsInTheFutureThenInstallAgeIsNull() = runTest {
        whenever(appInstallStore.installTimestamp).thenReturn(NOW + 1.days.inWholeMilliseconds)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW)

        assertNull(testee.getInstallAge())
    }

    @Test
    fun whenInstalledInThePastThenInstallAgeIsElapsedTime() = runTest {
        whenever(appInstallStore.installTimestamp).thenReturn(NOW - 5.days.inWholeMilliseconds)
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(NOW)

        assertEquals(5.days, testee.getInstallAge())
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
    }
}
