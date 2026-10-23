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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import app.cash.turbine.test
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.duckchat.store.impl.DuckAiBridgeStorage
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealDuckAiUsageLimitsRepositoryTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val settingsRows = MutableSharedFlow<DuckAiBridgeSettingEntity?>()
    private val settingsDao: DuckAiBridgeSettingsDao = mock {
        whenever(it.observe(RealDuckAiUsageLimitsRepository.USAGE_LIMITS_KEY)).thenReturn(settingsRows)
    }
    private val storage: DuckAiBridgeStorage = mock {
        whenever(it.settings).thenReturn(settingsDao)
    }
    private val storageProvider: BrowserModeDataProvider<DuckAiBridgeStorage> = mock {
        whenever(it.forMode(BrowserMode.REGULAR)).thenReturn(storage)
    }
    private val currentTimeProvider: CurrentTimeProvider = mock {
        whenever(it.currentTimeMillis()).thenReturn(NOW_MILLIS)
    }
    private val testee = RealDuckAiUsageLimitsRepository(
        storageProvider = storageProvider,
        parser = UsageLimitsSnapshotParser(currentTimeProvider),
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenFireModeThenNullIsEmittedAndStorageIsNeverRead() = runTest {
        testee.usageLimits(BrowserMode.FIRE).test {
            assertNull(awaitItem())
            awaitComplete()
        }

        verify(storageProvider, never()).forMode(any())
    }

    @Test
    fun whenRegularModeRowHoldsANoticeThenSnapshotIsEmitted() = runTest {
        testee.usageLimits(BrowserMode.REGULAR).test {
            settingsRows.emit(row(APPROACHING_75))

            val snapshot = awaitItem()!!
            assertEquals(UsageNoticeId.APPROACHING, snapshot.notice.id)
            assertEquals(75, snapshot.notice.percentUsed)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRowIsClearedOrDeletedThenNullIsEmitted() = runTest {
        testee.usageLimits(BrowserMode.REGULAR).test {
            settingsRows.emit(row(APPROACHING_75))
            awaitItem()

            settingsRows.emit(row("{}"))
            assertNull(awaitItem())

            settingsRows.emit(row(APPROACHING_75))
            awaitItem()

            settingsRows.emit(null)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTheSameRowIsRepublishedThenItIsNotReEmitted() = runTest {
        testee.usageLimits(BrowserMode.REGULAR).test {
            settingsRows.emit(row(APPROACHING_75))
            awaitItem()

            settingsRows.emit(row(APPROACHING_75))
            settingsRows.emit(row(APPROACHING_75))

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun row(value: String) = DuckAiBridgeSettingEntity(key = RealDuckAiUsageLimitsRepository.USAGE_LIMITS_KEY, value = value)

    private companion object {
        const val NOW_MILLIS = 1787659200000L
        const val APPROACHING_75 =
            """{"notice":{"id":"approaching","window":"weekly","percentUsed":75,"resetsAt":"2026-08-31T00:00:00.000Z"}}"""
    }
}
