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
import com.duckduckgo.sync.impl.ConnectedDevicesRepository
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckState
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason
import com.duckduckgo.sync.impl.auditlog.AuditLogPixels
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry
import com.duckduckgo.sync.impl.auditlog.LoginAuditLogFeature
import com.duckduckgo.sync.impl.auditlog.NewLoginNotifier
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.SYNCED
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Command
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Content
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Footer
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Status
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.ViewState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant

class SyncAuditLogViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val loader = FakeLoader()
    private val stateStore = FakeStateStore()
    private val newLoginNotifier: NewLoginNotifier = mock()
    private val loginAuditLogFeature = FakeLoginAuditLogFeature()
    private val auditLogPixels: AuditLogPixels = mock()
    private val connectedDevicesRepository = FakeConnectedDevicesRepository()
    private val timeProvider: CurrentTimeProvider = mock<CurrentTimeProvider>().also {
        whenever(it.currentTimeMillis()).thenReturn(NOW.toEpochMilli())
    }

    @Test
    fun whenOpenedFromNotificationAndAuditLogIsOffThenScreenClosesWithoutLoading() = runTest {
        loginAuditLogFeature.auditLogEnabled = false

        val testee = viewModel(openedFromNotification = true)

        assertEquals(Command.Close, testee.commands.first())
        assertEquals(0, loader.calls)
        verify(newLoginNotifier, never()).cancel()
        verify(auditLogPixels, never()).fireDeviceActivityScreenShown()
    }

    @Test
    fun whenOpenedThenScreenShownPixelFiredOnce() = runTest {
        val testee = viewModel()
        loader.result.complete(loaded(ITEM_1, before = "b1"))
        testee.onNearListEnd()

        verify(auditLogPixels, times(1)).fireDeviceActivityScreenShown()
    }

    @Test
    fun whenOpenedFromNotificationAndAuditLogIsOnThenScreenShownPixelFired() = runTest {
        viewModel(openedFromNotification = true)

        verify(auditLogPixels).fireDeviceActivityScreenShown()
    }

    @Test
    fun whenOpenedFromNotificationAndAuditLogIsOnThenLoads() = runTest {
        viewModel(openedFromNotification = true)

        assertEquals(1, loader.calls)
    }

    @Test
    fun whenNotOpenedFromNotificationThenLoadsWhateverTheFlag() = runTest {
        loginAuditLogFeature.auditLogEnabled = false

        viewModel()

        assertEquals(1, loader.calls)
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
    fun whenLogIsEmptyAndOnlyThisDeviceIsSyncedThenOnlyThisDevice() = runTest {
        connectedDevicesRepository.count = 1
        val testee = viewModel()

        loader.result.complete(loaded())

        assertEquals(Content.ONLY_THIS_DEVICE, testee.viewState.value.content)
    }

    @Test
    fun whenLogIsEmptyAndOtherDevicesAreSyncedThenEmpty() = runTest {
        connectedDevicesRepository.count = 2
        val testee = viewModel()

        loader.result.complete(loaded())

        assertEquals(Content.EMPTY, testee.viewState.value.content)
    }

    @Test
    fun whenLogIsEmptyAndDeviceListWasNeverReadThenEmpty() = runTest {
        connectedDevicesRepository.count = 0
        val testee = viewModel()

        loader.result.complete(loaded())

        assertEquals(Content.EMPTY, testee.viewState.value.content)
    }

    @Test
    fun whenLogHasRowsAndOnlyThisDeviceIsSyncedThenList() = runTest {
        connectedDevicesRepository.count = 1
        val testee = viewModel()

        loader.result.complete(loaded(ITEM_1))

        assertEquals(Content.LIST, testee.viewState.value.content)
    }

    @Test
    fun whenLoadFailsAndOnlyThisDeviceIsSyncedThenError() = runTest {
        connectedDevicesRepository.count = 1
        val testee = viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        assertEquals(Content.ERROR, testee.viewState.value.content)
    }

    @Test
    fun whenOlderLoadReachesStartOfLogWithNoRowsAndOnlyThisDeviceIsSyncedThenOnlyThisDevice() = runTest {
        connectedDevicesRepository.count = 1
        val testee = loadedViewModel(loaded(before = "b1"))

        testee.onNearListEnd()
        loader.older.complete(loaded(before = null))

        assertEquals(Content.ONLY_THIS_DEVICE, testee.viewState.value.content)
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

    @Test
    fun whenLoadIsRunningThenPendingLoginsAreKept() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "a1", pendingLogins = listOf(pending("e2")))

        viewModel()

        assertEquals(AuditLogCheckState(cursor = "a1", pendingLogins = listOf(pending("e2"))), stateStore.state)
    }

    @Test
    fun whenLoadFailsThenPendingLoginsAreKept() = runTest {
        stateStore.state = AuditLogCheckState(pendingLogins = listOf(pending("e2")))
        viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        assertEquals(listOf(pending("e2")), stateStore.state.pendingLogins)
    }

    @Test
    fun whenLoadedThenOnlyTheLoginsPendingAtOpenAreCleared() = runTest {
        stateStore.state = AuditLogCheckState(pendingLogins = listOf(pending("e2")))
        viewModel()
        stateStore.state = stateStore.state.copy(pendingLogins = listOf(pending("e2"), pending("e9")))

        loader.result.complete(loaded(ITEM_1))

        assertEquals(listOf(pending("e9")), stateStore.state.pendingLogins)
    }

    @Test
    fun whenLoadedThenNotificationIsCancelled() = runTest {
        loadedViewModel(loaded(ITEM_1))

        verify(newLoginNotifier).cancel()
    }

    @Test
    fun whenLoadFailsThenNotificationIsKept() = runTest {
        viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        verify(newLoginNotifier, never()).cancel()
    }

    @Test
    fun whenPendingLoginIsLoadedThenItsRowIsHighlighted() = runTest {
        stateStore.state = AuditLogCheckState(pendingLogins = listOf(pending("e2")))
        val testee = viewModel()

        loader.result.complete(loaded(ITEM_1, ITEM_2))

        assertEquals(listOf(row(ITEM_1, highlighted = true), row(ITEM_2)), testee.viewState.value.rows)
    }

    @Test
    fun whenHighlightTimeHasPassedThenRowsAreNotHighlighted() = runTest {
        stateStore.state = AuditLogCheckState(pendingLogins = listOf(pending("e2")))
        val testee = loadedViewModel(loaded(ITEM_1, ITEM_2))

        coroutineTestRule.testDispatcher.scheduler.advanceTimeBy(2_999)
        assertEquals(true, testee.viewState.value.rows.first().highlighted)
        coroutineTestRule.testDispatcher.scheduler.advanceTimeBy(2)

        assertEquals(listOf(row(ITEM_1), row(ITEM_2)), testee.viewState.value.rows)
    }

    @Test
    fun whenEntriesAreAboveTheLastSeenEntryThenTheyAreHighlighted() = runTest {
        stateStore.state = AuditLogCheckState(lastSeenEntryId = "e1")
        val testee = viewModel()

        loader.result.complete(loaded(ITEM_1, ITEM_2))

        assertEquals(listOf(row(ITEM_1, highlighted = true), row(ITEM_2)), testee.viewState.value.rows)
    }

    @Test
    fun whenLoadedThenNewestEntryIsStoredAsSeen() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "a1", lastSeenEntryId = "e0")

        loadedViewModel(loaded(ITEM_1, ITEM_2))

        assertEquals(AuditLogCheckState(cursor = "a1", lastSeenEntryId = "e2"), stateStore.state)
    }

    @Test
    fun whenLoadFailsThenLastSeenEntryIsKept() = runTest {
        stateStore.state = AuditLogCheckState(lastSeenEntryId = "e0")
        viewModel()

        loader.result.complete(SyncAuditLogLoadResult.Failed)

        assertEquals("e0", stateStore.state.lastSeenEntryId)
    }

    @Test
    fun whenOlderPageLoadsThenSeenStateIsNotChangedAgain() = runTest {
        stateStore.state = AuditLogCheckState(lastSeenEntryId = "e0")
        val testee = loadedViewModel(loaded(ITEM_1, before = "b1"))
        stateStore.state = stateStore.state.copy(lastSeenEntryId = "e5")

        testee.onNearListEnd()
        loader.older.complete(loaded(ITEM_2, before = null))

        assertEquals("e5", stateStore.state.lastSeenEntryId)
    }

    @Test
    fun whenOlderUnseenRowsLoadThenTheyAreHighlightedForAWhile() = runTest {
        stateStore.state = AuditLogCheckState(pendingLogins = listOf(pending("e1")))
        val first = loaded(ITEM_1, before = "b1")
        val testee = loadedViewModel(first)
        coroutineTestRule.testDispatcher.scheduler.advanceTimeBy(10_000)

        testee.onNearListEnd()
        loader.older.complete(
            SyncAuditLogLoadResult.Loaded(listOf(ITEM_2), SyncAuditLogPages(first.pages.entries + entry("e1"), before = null)),
        )

        assertEquals(listOf(row(ITEM_1), row(ITEM_2, highlighted = true)), testee.viewState.value.rows)
        coroutineTestRule.testDispatcher.scheduler.advanceTimeBy(3_001)
        assertEquals(listOf(row(ITEM_1), row(ITEM_2)), testee.viewState.value.rows)
    }

    @Test
    fun whenLastSeenEntryIsOnAnOlderPageThenRowsAboveItOnThatPageAreHighlighted() = runTest {
        stateStore.state = AuditLogCheckState(lastSeenEntryId = "e0")
        val first = loaded(ITEM_1, before = "b1")
        val testee = loadedViewModel(first)

        testee.onNearListEnd()
        loader.older.complete(
            SyncAuditLogLoadResult.Loaded(listOf(ITEM_2), SyncAuditLogPages(first.pages.entries + entry("e1") + entry("e0"), before = null)),
        )

        assertEquals(listOf(row(ITEM_1, highlighted = true), row(ITEM_2, highlighted = true)), testee.viewState.value.rows)
    }

    private fun viewModel(entriesPerPage: Int? = null, openedFromNotification: Boolean = false) =
        SyncAuditLogViewModel(
            SyncAuditLogScreenParams(entriesPerPage, openedFromNotification),
            loader,
            timeProvider,
            stateStore,
            newLoginNotifier,
            loginAuditLogFeature,
            auditLogPixels,
            connectedDevicesRepository,
        )

    private fun loadedViewModel(first: SyncAuditLogLoadResult.Loaded, entriesPerPage: Int? = null) =
        viewModel(entriesPerPage).also { loader.result.complete(first) }

    private fun failedOlderLoad() = loadedViewModel(loaded(ITEM_1, before = "b1")).also {
        it.onNearListEnd()
        loader.older.complete(SyncAuditLogLoadResult.Failed)
        loader.older = CompletableDeferred()
    }

    private fun loaded(vararg items: SyncAuditLogItem, before: String? = null) =
        SyncAuditLogLoadResult.Loaded(items.toList(), SyncAuditLogPages(entries = items.map { entry(it.entryId) }, before = before))

    private fun entry(id: String) = DecodedAuditLogEntry.Skipped(id, AuditLogEntrySkipReason.UNKNOWN_TYPE)

    private fun pending(id: String) = PendingLogin(id, "device-$id", "Name", NOW)

    private fun row(item: SyncAuditLogItem, highlighted: Boolean = false) = SyncAuditLogRow(item, RelativeTime.Hours(3), highlighted)

    private class FakeStateStore : AuditLogCheckStateStore {
        var state = AuditLogCheckState()

        override suspend fun read(): AuditLogCheckState = state

        override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) {
            transform(state)?.let { state = it }
        }

        override fun pendingLogins(): Flow<List<PendingLogin>> = flowOf(state.pendingLogins)
    }

    private class FakeLoginAuditLogFeature : LoginAuditLogFeature {
        var auditLogEnabled = true

        override suspend fun isAuditLogEnabled(): Boolean = auditLogEnabled

        override suspend fun isNotificationEnabled(): Boolean = auditLogEnabled
    }

    private class FakeConnectedDevicesRepository : ConnectedDevicesRepository {
        var count = 2

        override fun getConnectedDevices(reportAccountErrorPixel: Boolean) = error("Device Activity must not fetch the device list")

        override fun latestConnectedDeviceCountFlow(): Flow<Int> = emptyFlow()

        override suspend fun latestConnectedDeviceCount(): Int = count
    }

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
