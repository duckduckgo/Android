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

package com.duckduckgo.duckchat.internal.clienttools

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.clienttools.AiChatBrowserToolsFeature
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.BrowserToolsFlag
import com.duckduckgo.duckchat.internal.clienttools.DuckAiClientToolsDevViewModel.FlagRow
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class DuckAiClientToolsDevViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AiChatBrowserToolsFeature::class.java)

    private fun viewModel() = DuckAiClientToolsDevViewModel(feature, coroutineRule.testDispatcherProvider)

    @Test
    fun whenOpenedThenShowsEachBrowserToolsFlagWithItsState() = runTest {
        feature.listOpenTabs().setRawStoredState(Toggle.State(enable = true))

        val viewModel = viewModel()

        assertEquals(
            listOf(
                FlagRow(BrowserToolsFlag.BROWSER_TOOLS, enabled = false),
                FlagRow(BrowserToolsFlag.SKIP_PERMISSION_PROMPT, enabled = false),
                FlagRow(BrowserToolsFlag.LIST_OPEN_TABS, enabled = true),
            ),
            viewModel.viewState.value.flags,
        )
    }

    @Test
    fun whenFlagToggledOnThenOnlyThatToggleIsEnabledAndRowUpdates() = runTest {
        val viewModel = viewModel()

        viewModel.onFlagToggled(BrowserToolsFlag.SKIP_PERMISSION_PROMPT, true)

        assertTrue(feature.devSkipPermissionPrompt().isEnabled())
        assertFalse(feature.self().isEnabled())
        assertFalse(feature.listOpenTabs().isEnabled())
        assertTrue(viewModel.viewState.value.flags.single { it.flag == BrowserToolsFlag.SKIP_PERMISSION_PROMPT }.enabled)
    }

    @Test
    fun whenFlagToggledOffThenToggleIsDisabledEvenWhenRemoteHadItOn() = runTest {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, enable = true))
        val viewModel = viewModel()

        viewModel.onFlagToggled(BrowserToolsFlag.BROWSER_TOOLS, false)

        assertFalse(feature.self().isEnabled())
        assertFalse(viewModel.viewState.value.flags.single { it.flag == BrowserToolsFlag.BROWSER_TOOLS }.enabled)
    }
}
