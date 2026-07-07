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

package com.duckduckgo.autoconsent.impl

import com.duckduckgo.autoconsent.api.CookiePopUpPreference
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoconsentHeuristicModeProviderTest {

    private val settingsRepository = FakeSettingsRepository()
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)
    private lateinit var provider: RealAutoconsentHeuristicModeProvider

    @Before
    fun setup() {
        feature.heuristicAction().setRawStoredState(Toggle.State(enable = true))
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = true))
        provider = RealAutoconsentHeuristicModeProvider(settingsRepository, feature)
    }

    @Test
    fun whenHeuristicActionDisabledThenHeuristicModeIsOff() {
        feature.heuristicAction().setRawStoredState(Toggle.State(enable = false))
        settingsRepository.cookiePopUpPreference = CookiePopUpPreference.MAX

        assertEquals("off", provider.getHeuristicMode())
    }

    @Test
    fun whenPreferenceIsMaxThenHeuristicModeIsTier2() {
        settingsRepository.cookiePopUpPreference = CookiePopUpPreference.MAX

        assertEquals("tier2", provider.getHeuristicMode())
    }

    @Test
    fun whenPreferenceIsDefaultAndSettingEnabledThenHeuristicModeIsTier1() {
        settingsRepository.cookiePopUpPreference = CookiePopUpPreference.DEFAULT

        assertEquals("tier1", provider.getHeuristicMode())
    }

    @Test
    fun whenPreferenceIsDefaultAndSettingDisabledThenHeuristicModeIsReject() {
        feature.cookiePopUpPreferenceSetting().setRawStoredState(Toggle.State(enable = false))
        settingsRepository.cookiePopUpPreference = CookiePopUpPreference.DEFAULT

        assertEquals("reject", provider.getHeuristicMode())
    }

    @Test
    fun whenPreferenceIsOffThenHeuristicModeIsOff() {
        settingsRepository.cookiePopUpPreference = CookiePopUpPreference.OFF

        assertEquals("off", provider.getHeuristicMode())
    }
}
