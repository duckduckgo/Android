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

package com.duckduckgo.sync.impl.ui.auditlog

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.SYNCED
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.ViewState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class SyncAuditLogViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val loader = FakeLoader()
    private val timeProvider: CurrentTimeProvider = mock<CurrentTimeProvider>().also {
        whenever(it.currentTimeMillis()).thenReturn(NOW.toEpochMilli())
    }

    @Test
    fun whenLoadIsRunningThenLoading() = runTest {
        val testee = viewModel()

        assertEquals(ViewState.Loading, testee.viewState.value)
    }

    @Test
    fun whenLoadFailsThenError() = runTest {
        val testee = viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        assertEquals(ViewState.Error, testee.viewState.value)
    }

    @Test
    fun whenLogIsEmptyThenEmpty() = runTest {
        val testee = viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Loaded(emptyList()))

        assertEquals(ViewState.Empty, testee.viewState.value)
    }

    @Test
    fun whenLoadedThenRowsHaveTimeRelativeToWhenTheyLoaded() = runTest {
        val item = SyncAuditLogItem("e1", SYNCED, NOW.minus(Duration.ofHours(3)), "Phone", DeviceType("phone"))
        val testee = viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Loaded(listOf(item)))

        assertEquals(ViewState.Loaded(listOf(SyncAuditLogRow(item, RelativeTime.Hours(3)))), testee.viewState.value)
    }

    @Test
    fun whenStateIsCollectedAgainThenLogIsNotLoadedAgain() = runTest {
        val testee = viewModel()
        loader.result.complete(SyncAuditLogLoadResult.Loaded(emptyList()))

        testee.viewState.first()
        testee.viewState.first()

        assertEquals(1, loader.calls)
    }

    private fun viewModel() = SyncAuditLogViewModel(loader, timeProvider)

    private class FakeLoader : SyncAuditLogLoader {
        val result = CompletableDeferred<SyncAuditLogLoadResult>()
        var calls = 0

        override suspend fun loadLatest(): SyncAuditLogLoadResult {
            calls++
            return result.await()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T12:00:00Z")
    }
}
