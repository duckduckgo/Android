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
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Content
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Footer
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Status
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
    fun whenLoadIsRunningThenFullScreenLoading() = runTest {
        val testee = viewModel()

        assertEquals(Content.LOADING, testee.viewState.value.content)
    }

    @Test
    fun whenLoadFailsThenFullScreenError() = runTest {
        val testee = viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        assertEquals(Content.ERROR, testee.viewState.value.content)
    }

    @Test
    fun whenLogIsEmptyThenEmpty() = runTest {
        val testee = viewModel()

        loader.result.complete(loaded())

        assertEquals(Content.EMPTY, testee.viewState.value.content)
    }

    @Test
    fun whenLoadedThenRowsHaveTimeRelativeToWhenTheyLoaded() = runTest {
        val item = SyncAuditLogItem("e1", SYNCED, NOW.minus(Duration.ofHours(3)), "Phone", DeviceType("phone"))
        val testee = viewModel()

        loader.result.complete(loaded(item))

        assertEquals(ViewState(listOf(SyncAuditLogRow(item, RelativeTime.Hours(3))), Status.IDLE, hasMore = false), testee.viewState.value)
        assertEquals(Content.LIST, testee.viewState.value.content)
    }

    @Test
    fun whenStateIsCollectedAgainThenLogIsNotLoadedAgain() = runTest {
        val testee = viewModel()
        loader.result.complete(loaded())

        testee.viewState.first()
        testee.viewState.first()

        assertEquals(1, loader.calls)
    }

    @Test
    fun whenFirstLoadHasNoRowsButMoreThenListShownSoScrollingContinues() = runTest {
        val testee = viewModel()

        loader.result.complete(loaded(before = "b1"))

        assertEquals(Content.LIST, testee.viewState.value.content)
    }

    @Test
    fun whenNearListEndAndMoreThenLoadingFooterShown() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))

        testee.onNearListEnd()

        assertEquals(listOf(row(ITEM_1)), testee.viewState.value.rows)
        assertEquals(Footer.LOADING, testee.viewState.value.footer)
    }

    @Test
    fun whenOlderLoadRunsWithNoRowsYetThenFullScreenLoading() = runTest {
        val testee = loadedViewModel(loaded(before = "b1"))

        testee.onNearListEnd()

        assertEquals(Content.LOADING, testee.viewState.value.content)
        assertEquals(Footer.NONE, testee.viewState.value.footer)
    }

    @Test
    fun whenOlderRowsLoadThenAppendedAfterNewerRows() = runTest {
        val first = loaded(ITEM_1, before = "b1")
        val testee = loadedViewModel(first)

        testee.onNearListEnd()
        loader.older.complete(loaded(ITEM_2, before = null))

        assertEquals(ViewState(listOf(row(ITEM_1), row(ITEM_2)), Status.IDLE, hasMore = false), testee.viewState.value)
        assertEquals(listOf(first), loader.olderCalls)
    }

    @Test
    fun whenOlderRowsFailThenRowsKeptAndErrorFooterShown() = runTest {
        val testee = failedOlderLoad()

        assertEquals(listOf(row(ITEM_1)), testee.viewState.value.rows)
        assertEquals(Content.LIST, testee.viewState.value.content)
        assertEquals(Footer.ERROR, testee.viewState.value.footer)
    }

    @Test
    fun whenOlderRowsFailedAndListEndStillNearThenNoRetry() = runTest {
        val testee = failedOlderLoad()

        testee.onNearListEnd()

        assertEquals(1, loader.olderCalls.size)
        assertEquals(Footer.ERROR, testee.viewState.value.footer)
    }

    @Test
    fun whenOlderRowsFailedAndUserScrollsThenRetries() = runTest {
        val testee = failedOlderLoad()

        testee.onUserScrolled()
        testee.onNearListEnd()

        assertEquals(2, loader.olderCalls.size)
        assertEquals(Footer.LOADING, testee.viewState.value.footer)
    }

    @Test
    fun whenRetryFailsAgainThenNextRetryWaitsForAnotherScroll() = runTest {
        val testee = failedOlderLoad()
        testee.onUserScrolled()
        testee.onNearListEnd()
        loader.older.complete(SyncAuditLogLoadResult.Failed)

        testee.onNearListEnd()

        assertEquals(2, loader.olderCalls.size)
    }

    @Test
    fun whenUserScrolledWhileLoadingAndLoadFailsThenNoRetryUntilNextScroll() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))
        testee.onNearListEnd()
        testee.onUserScrolled()
        loader.older.complete(SyncAuditLogLoadResult.Failed)

        testee.onNearListEnd()

        assertEquals(1, loader.olderCalls.size)
    }

    @Test
    fun whenErrorRowTappedThenRetriesWithoutScrolling() = runTest {
        val testee = failedOlderLoad()

        testee.onRetryClicked()

        assertEquals(2, loader.olderCalls.size)
        assertEquals(Footer.LOADING, testee.viewState.value.footer)
    }

    @Test
    fun whenRetryTappedWithoutErrorThenNothingLoaded() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))

        testee.onRetryClicked()

        assertEquals(0, loader.olderCalls.size)
    }

    @Test
    fun whenOlderLoadReachesStartOfLogWithNoRowsThenEmpty() = runTest {
        val testee = loadedViewModel(loaded(before = "b1"))

        testee.onNearListEnd()
        loader.older.complete(loaded(before = null))

        assertEquals(Content.EMPTY, testee.viewState.value.content)
    }

    @Test
    fun whenNearListEndWhileLoadingThenOneLoad() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))

        testee.onNearListEnd()
        testee.onUserScrolled()
        testee.onNearListEnd()

        assertEquals(1, loader.olderCalls.size)
    }

    @Test
    fun whenNoMoreThenListEndDoesNothing() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = null))

        testee.onNearListEnd()

        assertEquals(ViewState(listOf(row(ITEM_1)), Status.IDLE, hasMore = false), testee.viewState.value)
        assertEquals(0, loader.olderCalls.size)
    }

    @Test
    fun whenSecondOlderLoadThenItContinuesFromTheFirst() = runTest {
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))
        val second = loaded(ITEM_2, before = "b2")
        testee.onNearListEnd()
        loader.older.complete(second)
        loader.older = CompletableDeferred()

        testee.onNearListEnd()

        assertEquals(second, loader.olderCalls.last())
    }

    @Test
    fun whenNearListEndBeforeFirstLoadThenNothingMoreLoaded() = runTest {
        val testee = viewModel()

        testee.onNearListEnd()

        assertEquals(1, loader.calls)
        assertEquals(0, loader.olderCalls.size)
    }

    @Test
    fun whenOpenedWithEntriesPerPageThenEveryLoadUsesIt() = runTest {
        loadedViewModel(loaded(ITEM_1, before = "b1"), entriesPerPage = 5).onNearListEnd()

        assertEquals(listOf<Int?>(5, 5), loader.entriesPerPageRequested)
    }

    private fun viewModel(entriesPerPage: Int? = null) =
        SyncAuditLogViewModel(SyncAuditLogScreenParams(entriesPerPage), loader, timeProvider)

    private fun loadedViewModel(first: SyncAuditLogLoadResult.Loaded, entriesPerPage: Int? = null) =
        viewModel(entriesPerPage).also { loader.result.complete(first) }

    private fun failedOlderLoad() = loadedViewModel(loaded(ITEM_1, before = "b1")).also {
        it.onNearListEnd()
        loader.older.complete(SyncAuditLogLoadResult.Failed)
        loader.older = CompletableDeferred()
    }

    private fun loaded(vararg items: SyncAuditLogItem, before: String? = null) =
        SyncAuditLogLoadResult.Loaded(items.toList(), SyncAuditLogPages(entries = emptyList(), before = before))

    private fun row(item: SyncAuditLogItem) = SyncAuditLogRow(item, RelativeTime.Hours(3))

    private class FakeLoader : SyncAuditLogLoader {
        val result = CompletableDeferred<SyncAuditLogLoadResult>()
        var older = CompletableDeferred<SyncAuditLogLoadResult>()
        var calls = 0
        val olderCalls = mutableListOf<SyncAuditLogLoadResult.Loaded>()
        val entriesPerPageRequested = mutableListOf<Int?>()

        override suspend fun loadLatest(entriesPerPage: Int?): SyncAuditLogLoadResult {
            calls++
            entriesPerPageRequested += entriesPerPage
            return result.await()
        }

        override suspend fun loadOlder(
            loaded: SyncAuditLogLoadResult.Loaded,
            entriesPerPage: Int?,
        ): SyncAuditLogLoadResult {
            olderCalls += loaded
            entriesPerPageRequested += entriesPerPage
            return older.await()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-12T12:00:00Z")
        val ITEM_1 = SyncAuditLogItem("e2", SYNCED, NOW.minus(Duration.ofHours(3)), "Phone", DeviceType("phone"))
        val ITEM_2 = SyncAuditLogItem("e1", SYNCED, NOW.minus(Duration.ofHours(3)), "Laptop", DeviceType("desktop"))
    }
}
