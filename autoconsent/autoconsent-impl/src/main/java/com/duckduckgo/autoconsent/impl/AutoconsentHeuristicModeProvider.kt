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
import com.duckduckgo.autoconsent.impl.store.AutoconsentSettingsRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutoconsentHeuristicModeProvider {
    fun getHeuristicMode(): String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAutoconsentHeuristicModeProvider @Inject constructor(
    private val settingsRepository: AutoconsentSettingsRepository,
    private val autoconsentFeature: AutoconsentFeature,
) : AutoconsentHeuristicModeProvider {

    override fun getHeuristicMode(): String {
        if (!autoconsentFeature.heuristicAction().isEnabled()) {
            return "off"
        }

        val cookiePopUpPreferenceSettingEnabled = autoconsentFeature.cookiePopUpPreferenceSetting().isEnabled()
        return when (settingsRepository.cookiePopUpPreference) {
            CookiePopUpPreference.MAX -> "tier2"
            CookiePopUpPreference.DEFAULT -> if (cookiePopUpPreferenceSettingEnabled) "tier1" else "reject"
            CookiePopUpPreference.OFF -> "off"
        }
    }
}
