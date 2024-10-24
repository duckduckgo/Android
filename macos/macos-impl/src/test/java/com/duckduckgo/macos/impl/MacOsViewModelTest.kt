/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.macos.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.macos.impl.MacOsPixelNames.MACOS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.macos.impl.MacOsViewModel.Command.GoToWindowsClientSettings
import com.duckduckgo.macos.impl.MacOsViewModel.Command.ShareLink
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class MacOsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var mockPixel: Pixel = mock()
    private var macOsDownloadLinkOrigin = FakeFeatureToggleFactory.create(MacOsDownloadLinkOrigin::class.java)

    private lateinit var testee: MacOsViewModel

    @Before
    fun before() {
        testee = MacOsViewModel(mockPixel, macOsDownloadLinkOrigin)
    }

    @Test
    fun whenOnShareClickedAndInviteCodeExistsThenEmitCommandShareInviteCodeWithCorrectCode() = runTest {
        macOsDownloadLinkOrigin.self().setRawStoredState(State(enable = true))
        testee.commands.test {
            testee.onShareClicked()
            assertEquals(ShareLink(true), awaitItem())
        }
    }

    fun whenOnGoToWindowsClickedCalledThenEmitGoToWindowsClientSettingsCommand() = runTest {
        testee.commands.test {
            testee.onGoToWindowsClicked()
            assertEquals(GoToWindowsClientSettings, awaitItem())
        }
    }

    @Test
    fun whenOnShareClickedAThenPixelFired() = runTest {
        testee.onShareClicked()

        verify(mockPixel).fire(MACOS_WAITLIST_SHARE_PRESSED)
    }
}
