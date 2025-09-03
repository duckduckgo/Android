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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery

import androidx.datastore.preferences.core.Preferences.Key
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER

internal enum class InputScreenDiscoveryFunnelStep(
    val prefKey: Key<Boolean>,
    val pixelName: DuckChatPixelName,
    val dependsOn: Set<InputScreenDiscoveryFunnelStep> = emptySet(),
) {

    SettingsSeen(
        prefKey = booleanPreferencesKey("SETTINGS_SEEN"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SETTINGS_VIEWED,
    ),

    FeatureEnabled(
        prefKey = booleanPreferencesKey("FEATURE_ENABLED"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_ENABLED,
        dependsOn = setOf(SettingsSeen),
    ),

    OmnibarInteracted(
        prefKey = booleanPreferencesKey("OMNIBAR_INTERACTED"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_INTERACTION,
        dependsOn = setOf(FeatureEnabled),
    ),

    SearchSubmitted(
        prefKey = booleanPreferencesKey("SEARCH_SUBMITTED"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_SEARCH_SUBMISSION,
        dependsOn = setOf(OmnibarInteracted),
    ),

    PromptSubmitted(
        prefKey = booleanPreferencesKey("PROMPT_SUBMITTED"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FIRST_PROMPT_SUBMISSION,
        dependsOn = setOf(OmnibarInteracted),
    ),

    FullyConverted(
        prefKey = booleanPreferencesKey("FULLY_CONVERTED"),
        pixelName = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FULL_CONVERSION_USER,
        dependsOn = setOf(SearchSubmitted, PromptSubmitted),
    ),
}
