/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.macos_impl.waitlist.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_SHARE_PRESSED
import com.duckduckgo.macos_impl.MacOsViewModel
import com.duckduckgo.macos_impl.MacOsViewModel.Command.ShareLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class MacOsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var mockPixel: Pixel = mock()
    private lateinit var testee: MacOsViewModel

    @Before
    fun before() {
        testee = MacOsViewModel(mockPixel)
    }

    @Test
    fun whenOnShareClickedAndInviteCodeExistsThenEmitCommandShareInviteCodeWithCorrectCode() = runTest {
        testee.commands.test {
            testee.onShareClicked()
            assertEquals(ShareLink, awaitItem())
        }
    }

    @Test
    fun whenOnShareClickedAThenPixelFired() = runTest {
        testee.onShareClicked()

        verify(mockPixel).fire(MACOS_WAITLIST_SHARE_PRESSED)
    }
}
