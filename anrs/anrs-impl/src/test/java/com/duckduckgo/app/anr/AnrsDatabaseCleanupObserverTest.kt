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

package com.duckduckgo.app.anr

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.anrs.store.AnrDao
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExperimentalCoroutinesApi
class AnrsDatabaseCleanupObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val anrDao: AnrDao = mock()
    private val anrsDatabase: AnrsDatabase = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = AnrsDatabaseCleanupObserver(
        anrsDatabase = anrsDatabase,
        appScope = TestScope(),
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenAppCreatedThenAnrsOlderThanExpiryWindowArePurged() = runTest {
        whenever(anrsDatabase.arnDao()).thenReturn(anrDao)

        testee.onCreate(lifecycleOwner)
        advanceUntilIdle()

        val timestampCaptor = argumentCaptor<String>()
        verify(anrDao).removeOldAnrs(timestampCaptor.capture())

        val lowerBound = DatabaseDateFormatter.timestamp(
            LocalDateTime.now().minusHours(AnrsDatabaseCleanupObserver.ANR_EXPIRY_WINDOW_HOURS).minusMinutes(1),
        )
        val upperBound = DatabaseDateFormatter.timestamp(
            LocalDateTime.now().minusHours(AnrsDatabaseCleanupObserver.ANR_EXPIRY_WINDOW_HOURS).plusMinutes(1),
        )
        assertTrue(timestampCaptor.firstValue > lowerBound)
        assertTrue(timestampCaptor.firstValue < upperBound)
    }
}
