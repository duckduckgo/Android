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

package com.duckduckgo.app.desktopbrowser

import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.desktopbrowser.GetDesktopBrowserActivityParams.Source
import com.duckduckgo.app.desktopbrowser.GetDesktopBrowserViewModel.Command
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class GetDesktopBrowserViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val settingsDataStoreMock: SettingsDataStore = mock()
    private val clipboardInteractorMock: ClipboardInteractor = mock()

    private var testee: GetDesktopBrowserViewModel = GetDesktopBrowserViewModel(
        params = GetDesktopBrowserActivityParams(Source.COMPLETE_SETUP),
        settingsDataStore = settingsDataStoreMock,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        clipboardInteractor = clipboardInteractorMock,
    )

    @Test
    fun whenSourceIsCompleteSetupThenShowNoThanksButtonIsTrue() = runTest {
        testee.viewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.showNoThanksButton)
        }
    }

    @Test
    fun whenSourceIsOtherThenShowNoThanksButtonIsFalse() = runTest {
        testee = GetDesktopBrowserViewModel(
            params = GetDesktopBrowserActivityParams(Source.OTHER),
            settingsDataStore = settingsDataStoreMock,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            clipboardInteractor = clipboardInteractorMock,
        )

        testee.viewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.showNoThanksButton)
        }
    }

    @Test
    fun whenOnShareDownloadLinkClickedThenEmitShareDownloadLinkCommand() = runTest {
        testee.commands.test {
            testee.onShareDownloadLinkClicked()

            val command = awaitItem() as Command.ShareDownloadLink
            assertEquals("https://duckduckgo.com/browser?origin=funnel_browser_settings_android", command.url)
        }
    }

    @Test
    fun whenOnNoThanksClickedThenSetDataStoreFlagAndEmitDismissedCommand() = runTest {
        testee.commands.test {
            testee.onNoThanksClicked()

            verify(settingsDataStoreMock).getDesktopBrowserSettingDismissed = true
            val command = awaitItem()
            assertEquals(Command.Dismissed, command)
        }
    }

    @Test
    fun whenOnBackPressedThenEmitCloseCommand() = runTest {
        testee.commands.test {
            testee.onBackPressed()

            val command = awaitItem()

            verifyNoInteractions(settingsDataStoreMock)
            assertEquals(Command.Close, command)
        }
    }

    @Test
    fun whenOnLinkClickedAndSystemShowsNotificationThenDoNotEmitShowCopiedNotificationCommand() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(true)

        testee.commands.test {
            testee.onLinkClicked()
            expectNoEvents()
        }
    }

    @Test
    fun whenOnLinkClickedAndSystemDoesNotShowNotificationThenEmitShowCopiedNotificationCommand() = runTest {
        whenever(clipboardInteractorMock.copyToClipboard(any(), any())).thenReturn(false)

        testee.commands.test {
            testee.onLinkClicked()

            val command = awaitItem()
            assertEquals(Command.ShowCopiedNotification, command)
        }
    }
}
