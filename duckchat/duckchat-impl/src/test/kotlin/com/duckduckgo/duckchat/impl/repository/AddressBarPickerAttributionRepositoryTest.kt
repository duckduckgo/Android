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

package com.duckduckgo.duckchat.impl.repository

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class AddressBarPickerAttributionRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dataStore: DuckChatDataStore = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock()

    private fun createTestee(): AddressBarPickerAttributionRepository =
        RealAddressBarPickerAttributionRepository(
            dataStore,
            currentTimeProvider,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )

    @Test
    fun `when onPickerDuckAiSelected then stores current time`() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L)
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()

        testee.onPickerDuckAiSelected()

        verify(dataStore).storeAddressBarPickerSelectedAt(1_000L)
    }

    @Test
    fun `when selected and consumed within window then returns true and clears the marker`() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L)
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()
        testee.onPickerDuckAiSelected()
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L + TimeUnit.HOURS.toMillis(1))

        val attributed = testee.consumeAttributionToPicker()
        coroutineRule.testScope.advanceUntilIdle()

        assertTrue(attributed)
        verify(dataStore).clearAddressBarPickerSelectedAt()
    }

    @Test
    fun `when selected and consumed after window then returns false`() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L)
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()
        testee.onPickerDuckAiSelected()
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L + TimeUnit.HOURS.toMillis(25))

        assertFalse(testee.consumeAttributionToPicker())
    }

    @Test
    fun `when nothing selected then consume returns false and does not clear`() = runTest {
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()

        assertFalse(testee.consumeAttributionToPicker())
        coroutineRule.testScope.advanceUntilIdle()
        verify(dataStore, never()).clearAddressBarPickerSelectedAt()
    }

    @Test
    fun `when consumed twice then second consume returns false`() = runTest {
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1_000L)
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()
        testee.onPickerDuckAiSelected()
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(2_000L)

        assertTrue(testee.consumeAttributionToPicker())
        assertFalse(testee.consumeAttributionToPicker())
    }

    @Test
    fun `when a selection was persisted then it is hydrated and attributed on consume`() = runTest {
        whenever(dataStore.getAddressBarPickerSelectedAt()).thenReturn(5_000L)
        val testee = createTestee()
        coroutineRule.testScope.advanceUntilIdle()
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(5_000L + TimeUnit.HOURS.toMillis(1))

        assertTrue(testee.consumeAttributionToPicker())
    }
}
