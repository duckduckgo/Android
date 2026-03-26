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

package com.duckduckgo.autoconsent.impl.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import app.cash.turbine.test
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class AutoconsentSettingsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val autoconsent: Autoconsent = FakeAutoconsent()
    private val pixel: FakePixel = FakePixel()

    private lateinit var viewModel: AutoconsentSettingsViewModel

    @Before
    fun setup() {
        pixel.firedPixels.clear()
    }

    @Test
    fun whenViewModelCreatedThenAutoConsentShownPixelFired() {
        initViewModel()

        assertEquals(1, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_SHOWN.pixelName, pixel.firedPixels.first())
    }

    @Test
    fun whenViewModelCreatedThenEmitViewState() = runTest {
        initViewModel()

        viewModel.viewState.test {
            assertFalse(awaitItem().autoconsentEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnLearnMoreSelectedCalledThenLaunchLearnMoreWebPageCommandIsSent() = runTest {
        initViewModel()

        viewModel.commands().test {
            viewModel.onLearnMoreSelected()
            assertEquals(AutoconsentSettingsViewModel.Command.LaunchLearnMoreWebPage(), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnUserToggleAutoconsentToTrueThenAutoconsentEnabledIsTrue() = runTest {
        initViewModel()

        viewModel.viewState.test {
            assertFalse(awaitItem().autoconsentEnabled)
            viewModel.onUserToggleAutoconsent(true)
            assertTrue(autoconsent.isSettingEnabled())
            assertTrue(awaitItem().autoconsentEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnUserToggleAutoconsentToTrueThenAutoconsentOnPixelIsFired() {
        initViewModel()

        viewModel.onUserToggleAutoconsent(true)

        assertEquals(2, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_ON.pixelName, pixel.firedPixels[1])
    }

    @Test
    fun whenOnUserToggleAutoconsentToFalseThenAutoconsentEnabledIsFalse() = runTest {
        initViewModel()

        viewModel.viewState.test {
            viewModel.onUserToggleAutoconsent(false)
            assertFalse(awaitItem().autoconsentEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnUserToggleAutoconsentToTrueThenAutoconsentOffPixelIsFired() {
        initViewModel()

        viewModel.onUserToggleAutoconsent(false)

        assertEquals(2, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_OFF.pixelName, pixel.firedPixels[1])
    }

    private fun initViewModel() {
        viewModel = AutoconsentSettingsViewModel(autoconsent, pixel)
    }

    internal class FakeAutoconsent : Autoconsent {
        var test: Boolean = false

        override fun injectAutoconsent(
            webView: WebView,
            url: String,
        ) {
            // NO OP
        }

        override fun addJsInterface(
            webView: WebView,
            autoconsentCallback: AutoconsentCallback,
        ) {
            // NO OP
        }

        override fun changeSetting(setting: Boolean) {
            test = setting
        }

        override fun isSettingEnabled(): Boolean = test

        override fun isAutoconsentEnabled(): Boolean {
            return isSettingEnabled()
        }

        override fun setAutoconsentOptOut(webView: WebView) {
            // NO OP
        }

        override fun setAutoconsentOptIn() {
            // NO OP
        }

        override fun firstPopUpHandled() {
            // NO OP
        }
    }

    internal class FakePixel : Pixel {

        val firedPixels = mutableListOf<String>()

        override fun fire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixel.pixelName)
        }

        override fun fire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixelName)
        }

        override fun enqueueFire(
            pixel: PixelName,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixel.pixelName)
        }

        override fun enqueueFire(
            pixelName: String,
            parameters: Map<String, String>,
            encodedParameters: Map<String, String>,
            type: PixelType,
        ) {
            firedPixels.add(pixelName)
        }
    }
}
