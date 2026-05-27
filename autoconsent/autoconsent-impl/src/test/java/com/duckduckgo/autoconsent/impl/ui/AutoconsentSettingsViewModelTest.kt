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
import com.duckduckgo.autoconsent.api.CookiePopUpPreference
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SuppressLint("DenyListedApi")
class AutoconsentSettingsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val fakeAutoconsent = FakeAutoconsent()
    private val autoconsent: Autoconsent = fakeAutoconsent
    private val pixel: FakePixel = FakePixel()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    private lateinit var viewModel: AutoconsentSettingsViewModel

    @Before
    fun setup() {
        pixel.firedPixels.clear()
        fakeAutoconsent.preference = CookiePopUpPreference.DO_NOT_BLOCK
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = false))
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
            assertEquals(CookiePopUpPreference.DO_NOT_BLOCK, awaitItem().selectedPreference)
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
    fun whenOnUserToggleAutoconsentToTrueThenLegacySettingUpdated() = runTest {
        initViewModel()

        viewModel.onUserToggleAutoconsent(true)

        assertTrue(autoconsent.isSettingEnabled())
    }

    @Test
    fun whenOnUserToggleAutoconsentToTrueThenAutoconsentOnPixelIsFired() {
        initViewModel()

        viewModel.onUserToggleAutoconsent(true)

        assertEquals(2, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_ON.pixelName, pixel.firedPixels[1])
    }

    @Test
    fun whenOnUserToggleAutoconsentToFalseThenAutoconsentOffPixelIsFired() {
        initViewModel()
        viewModel.onUserToggleAutoconsent(false)

        assertEquals(2, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_OFF.pixelName, pixel.firedPixels[1])
    }

    @Test
    fun whenOnCookiePopUpPreferenceSelectedToBlockStandardThenPreferenceUpdated() = runTest {
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
        initViewModel()

        viewModel.viewState.test {
            assertEquals(CookiePopUpPreference.DO_NOT_BLOCK, awaitItem().selectedPreference)
            viewModel.onCookiePopUpPreferenceSelected(CookiePopUpPreference.BLOCK_STANDARD)
            assertEquals(CookiePopUpPreference.BLOCK_STANDARD, autoconsent.getCookiePopUpPreference())
            assertEquals(CookiePopUpPreference.BLOCK_STANDARD, awaitItem().selectedPreference)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCookiePopUpPreferenceSelectedToDoNotBlockThenPreferenceUpdated() {
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
        initViewModel()
        viewModel.onCookiePopUpPreferenceSelected(CookiePopUpPreference.BLOCK_ALL)
        viewModel.onCookiePopUpPreferenceSelected(CookiePopUpPreference.DO_NOT_BLOCK)

        assertEquals(CookiePopUpPreference.DO_NOT_BLOCK, autoconsent.getCookiePopUpPreference())
        assertEquals(CookiePopUpPreference.DO_NOT_BLOCK, viewModel.viewState.value.selectedPreference)
    }

    @Test
    fun whenOnCookiePopUpPreferenceSelectedToBlockAllThenAutoconsentOnPixelIsFired() {
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
        initViewModel()

        viewModel.onCookiePopUpPreferenceSelected(CookiePopUpPreference.BLOCK_ALL)

        assertEquals(2, pixel.firedPixels.size)
        assertEquals(AutoConsentPixel.SETTINGS_AUTOCONSENT_ON.pixelName, pixel.firedPixels[1])
    }

    private fun initViewModel() {
        viewModel = AutoconsentSettingsViewModel(autoconsent, pixel, feature)
    }

    internal class FakeAutoconsent : Autoconsent {
        var preference: CookiePopUpPreference = CookiePopUpPreference.DO_NOT_BLOCK

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
            preference = if (setting) CookiePopUpPreference.BLOCK_STANDARD else CookiePopUpPreference.DO_NOT_BLOCK
        }

        override fun changeCookiePopUpPreference(preference: CookiePopUpPreference) {
            this.preference = preference
        }

        override fun getCookiePopUpPreference(): CookiePopUpPreference = preference

        override fun isSettingEnabled(): Boolean = preference != CookiePopUpPreference.DO_NOT_BLOCK

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
