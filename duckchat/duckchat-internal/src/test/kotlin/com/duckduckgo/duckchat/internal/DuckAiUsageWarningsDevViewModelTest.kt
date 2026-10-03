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

package com.duckduckgo.duckchat.internal

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.nativeinput.footer.highusage.HighUsageModelNoticeDismissalStore
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeActedOn
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeDismissal
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeDismissalStore
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageNoticeId
import com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits.UsageWindow
import com.duckduckgo.duckchat.internal.DuckAiUsageWarningsDevViewModel.Command
import com.duckduckgo.duckchat.internal.DuckAiUsageWarningsDevViewModel.ViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckAiUsageWarningsDevViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dismissedModelIds = MutableStateFlow<Set<String>>(emptySet())
    private val dismissalStore: HighUsageModelNoticeDismissalStore = mock<HighUsageModelNoticeDismissalStore>().also {
        whenever(it.dismissedModelIds).thenReturn(dismissedModelIds)
    }

    private val usageDismissals = MutableStateFlow<Map<UsageWindow, UsageNoticeDismissal>>(emptyMap())
    private val usageActedOn = MutableStateFlow<UsageNoticeActedOn?>(null)
    private val usageNoticeDismissalStore: UsageNoticeDismissalStore = mock<UsageNoticeDismissalStore>().also {
        whenever(it.dismissals).thenReturn(usageDismissals)
        whenever(it.actedOn).thenReturn(usageActedOn)
    }

    private val testee by lazy { DuckAiUsageWarningsDevViewModel(dismissalStore, usageNoticeDismissalStore) }

    @Test
    fun whenModelsWereDismissedThenStateListsThem() = runTest {
        dismissedModelIds.value = setOf("claude-opus-4-8")

        testee.viewState.test {
            assertEquals(ViewState(dismissedModelIds = setOf("claude-opus-4-8")), expectMostRecentItem())
        }
    }

    @Test
    fun whenDismissalsChangeThenStateUpdates() = runTest {
        testee.viewState.test {
            assertEquals(emptySet<String>(), expectMostRecentItem().dismissedModelIds)

            dismissedModelIds.value = setOf("claude-opus-4-8")

            assertEquals(setOf("claude-opus-4-8"), awaitItem().dismissedModelIds)
        }
    }

    @Test
    fun whenUsageNoticeWasDismissedThenStateCarriesIt() = runTest {
        val dismissal = UsageNoticeDismissal(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 1L, 75)
        usageDismissals.value = mapOf(UsageWindow.WEEKLY to dismissal)

        testee.viewState.test {
            assertEquals(dismissal, expectMostRecentItem().usageNoticeDismissals.single())
        }
    }

    @Test
    fun whenUsageSnapshotWasActedOnThenStateCarriesIt() = runTest {
        usageActedOn.value = UsageNoticeActedOn(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 1L, 75)

        testee.viewState.test {
            assertEquals(UsageNoticeActedOn(UsageNoticeId.APPROACHING, UsageWindow.WEEKLY, 1L, 75), expectMostRecentItem().usageNoticeActedOn)
        }
    }

    @Test
    fun whenResetUsageDismissalIsClickedThenStoreIsClearedAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onResetUsageNoticeDismissalClicked()

            verify(usageNoticeDismissalStore).clear()
            verify(usageNoticeDismissalStore).clearActedOn()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiUsageWarningsUsageDismissalReset), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenResetIsClickedThenStoreIsClearedAndMessageIsShown() = runTest {
        testee.commands.test {
            testee.onResetDismissalsClicked()

            verify(dismissalStore).clear()
            assertEquals(Command.ShowMessage(R.string.devSettingsDuckAiUsageWarningsDismissalsReset), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
