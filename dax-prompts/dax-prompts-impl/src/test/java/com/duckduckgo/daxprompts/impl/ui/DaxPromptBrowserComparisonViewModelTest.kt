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

package com.duckduckgo.daxprompts.impl.ui

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.daxprompts.impl.ui.DaxPromptBrowserComparisonViewModel.Companion.BROWSER_COMPARISON_MORE_URL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DaxPromptBrowserComparisonViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: DaxPromptBrowserComparisonViewModel

    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val mockDaxPromptsRepository: DaxPromptsRepository = org.mockito.kotlin.mock()
    private val mockApplicationContext: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        testee = DaxPromptBrowserComparisonViewModel(
            mockDefaultRoleBrowserDialog,
            mockDaxPromptsRepository,
            mockApplicationContext,
        )
    }

    @Test
    fun whenMoreLinkClickedThenEmitsOpenDetailsPageCommand() = runTest {
        testee.onMoreLinkClicked()

        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.OpenDetailsPage(BROWSER_COMPARISON_MORE_URL), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCloseButtonClickedThenEmitsCloseScreenCommand() = runTest {
        testee.onCloseButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.CloseScreen(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPrimaryButtonClickedAndShouldShowDialogAndIntentIsValidThenEmitsBrowserComparisonChartCommand() = runTest {
        val mockIntent: Intent = mock()
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockApplicationContext)).thenReturn(mockIntent)

        testee.onPrimaryButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.BrowserComparisonChart(mockIntent), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPrimaryButtonClickedAndShouldShowDialogAndIntentIsNullThenEmitsCloseScreenCommand() = runTest {
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockApplicationContext)).thenReturn(null)

        testee.onPrimaryButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.CloseScreen(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPrimaryButtonClickedAndShouldNotShowDialogThenEmitsCloseScreenCommand() = runTest {
        whenever(mockDefaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)

        testee.onPrimaryButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.CloseScreen(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserSetThenDialogMarkedAsShownAndEmitsCloseScreenCommandWithTrue() = runTest {
        testee.onDefaultBrowserSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.CloseScreen(true), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDefaultBrowserNotSetThenDialogMarkedAsShownAndEmitsCloseScreenCommandWithFalse() = runTest {
        testee.onDefaultBrowserNotSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        testee.commands().test {
            assertEquals(DaxPromptBrowserComparisonViewModel.Command.CloseScreen(false), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
