/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.globalprivacycontrol.ui.GlobalPrivacyControlViewModel.Companion.LEARN_MORE_URL
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Gpc
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.lastValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor

class GlobalPrivacyControlViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    private val mockPixel: Pixel = mock()
    private val mockFeatureToggle: FeatureToggle = mock()
    private val mockGpc: Gpc = mock()
    private val commandCaptor = ArgumentCaptor.forClass(GlobalPrivacyControlViewModel.Command::class.java)
    private val viewStateCaptor = ArgumentCaptor.forClass(GlobalPrivacyControlViewModel.ViewState::class.java)
    private val mockCommandObserver: Observer<GlobalPrivacyControlViewModel.Command> = mock()
    private val mockViewStateObserver: Observer<GlobalPrivacyControlViewModel.ViewState> = mock()
    lateinit var testee: GlobalPrivacyControlViewModel

    @Before
    fun setup() {
        testee = GlobalPrivacyControlViewModel(mockPixel, mockFeatureToggle, mockGpc)
        testee.command.observeForever(mockCommandObserver)
        testee.viewState.observeForever(mockViewStateObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
        testee.viewState.removeObserver(mockViewStateObserver)
    }

    @Test
    fun whenViewModelCreateThenInitialisedWithDefaultViewState() {
        val defaultViewState = GlobalPrivacyControlViewModel.ViewState()
        verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        assertEquals(defaultViewState, viewStateCaptor.value)
    }

    @Test
    fun whenViewModelCreateThenPixelSent() {
        verify(mockPixel).fire(AppPixelName.SETTINGS_DO_NOT_SELL_SHOWN)
    }

    @Test
    fun whenOnLearnMoreSelectedThenOpenLearnMoreCommandIssuedWithCorrectUrl() {
        testee.onLearnMoreSelected()

        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is GlobalPrivacyControlViewModel.Command.OpenLearnMore)
        val url = (commandCaptor.lastValue as GlobalPrivacyControlViewModel.Command.OpenLearnMore).url
        assertEquals(LEARN_MORE_URL, url)
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlThenDoNotSellOnPixelSent() {
        testee.onUserToggleGlobalPrivacyControl(true)

        verify(mockPixel).fire(AppPixelName.SETTINGS_DO_NOT_SELL_ON)
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlThenDoNotSellOffPixelSent() {
        testee.onUserToggleGlobalPrivacyControl(false)

        verify(mockPixel).fire(AppPixelName.SETTINGS_DO_NOT_SELL_OFF)
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlSwitchedOnThenValueStoredInSettings() {
        testee.onUserToggleGlobalPrivacyControl(true)

        verify(mockGpc).enableGpc()
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlSwitchedOffThenValueStoredInSettings() {
        testee.onUserToggleGlobalPrivacyControl(false)

        verify(mockGpc).disableGpc()
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlSwitchedOnThenViewStateUpdatedToTrue() {
        testee.onUserToggleGlobalPrivacyControl(true)

        verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        assertTrue(viewStateCaptor.value.globalPrivacyControlEnabled)
    }

    @Test
    fun whenOnUserToggleGlobalPrivacyControlSwitchedOnThenViewStateUpdatedToFalse() {
        testee.onUserToggleGlobalPrivacyControl(false)

        verify(mockViewStateObserver, atLeastOnce()).onChanged(viewStateCaptor.capture())
        assertFalse(viewStateCaptor.value.globalPrivacyControlEnabled)
    }
}
