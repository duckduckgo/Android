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

package com.duckduckgo.autoconsent.impl.prompt

import app.cash.turbine.test
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autoconsent.impl.FakeSettingsRepository
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Command
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Variant
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class CookiePopupOptInViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val autoconsent: Autoconsent = mock()
    private val settingsRepository = FakeSettingsRepository()

    private val testee by lazy {
        CookiePopupOptInViewModel(autoconsent, settingsRepository, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenProtectionAlreadyEnabledThenProtectionOnVariant() {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        assertEquals(Variant.PROTECTION_ON, testee.viewState.value.variant)
    }

    @Test
    fun whenProtectionDisabledThenProtectionOffVariant() {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        assertEquals(Variant.PROTECTION_OFF, testee.viewState.value.variant)
    }

    @Test
    fun whenAcceptedWithProtectionAlreadyEnabledThenOnlyClickAcceptEnabled() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(true)

        testee.onAcceptClicked()

        verify(autoconsent).changeClickAcceptEnabled(true)
        verify(autoconsent, never()).changeSetting(true)
    }

    @Test
    fun whenAcceptedWithProtectionDisabledThenProtectionAndClickAcceptEnabled() = runTest {
        whenever(autoconsent.isSettingEnabled()).thenReturn(false)

        testee.onAcceptClicked()

        verify(autoconsent).changeSetting(true)
        verify(autoconsent).changeClickAcceptEnabled(true)
    }

    @Test
    fun whenAcceptedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onAcceptClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeclinedThenNoSettingChanged() = runTest {
        testee.onDeclineClicked()

        verify(autoconsent, never()).changeSetting(true)
        verify(autoconsent, never()).changeClickAcceptEnabled(true)
    }

    @Test
    fun whenDeclinedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onDeclineClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAcceptedThenChoiceRecorded() = runTest {
        testee.onAcceptClicked()

        assertTrue(settingsRepository.optInPromptChoiceMade)
    }

    @Test
    fun whenDeclinedThenChoiceRecorded() = runTest {
        testee.onDeclineClicked()

        assertTrue(settingsRepository.optInPromptChoiceMade)
    }

    @Test
    fun whenNoChoiceMadeYetThenNotRecorded() {
        assertFalse(settingsRepository.optInPromptChoiceMade)
    }
}
