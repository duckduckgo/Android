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

package com.duckduckgo.duckchat.impl.feature

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled

/**
 * This is the class that represents the aiChat feature flags
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "aiChat",
)
interface DuckChatFeature {
    /**
     * @return `true` when the remote config has the global "aiChat" feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * @return `true` when the remote config has the "duckAiButtonInBrowser" Duck.ai button in browser
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiButtonInBrowser(): Toggle

    /**
     * @return `true` when the remote config has the "keepSessionAlive"
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun keepSession(): Toggle

    /**
     * @return `true` when the remote config has the "duckAiInputScreen"
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun duckAiInputScreen(): Toggle

    /**
     * @return `true` when the Input Screen should open automatically when user creates a New Tab
     * If the remote feature is not present defaults to `enabled`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun showInputScreenAutomaticallyOnNewTab(): Toggle

    /**
     * @return `true` when the Input Screen should be shown when user open the app from system widgets
     * If the remote feature is not present defaults to `disabled`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun showInputScreenOnSystemSearchLaunch(): Toggle

    /**
     * @return `true` when the Input Screen can present a bottom input box, if user has the omnibar also set to the bottom position.
     * If disabled, the Input Screen should always show the input box at the top of the screen.
     * If the remote feature is not present defaults to `enabled`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun inputScreenBottomBarSupport(): Toggle

    /**
     * @return `true` when the new address bar option choice screen should be shown
     * If the remote feature is not present defaults to `internal`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    @InternalAlwaysEnabled
    fun showAIChatAddressBarChoiceScreen(): Toggle

    /**
     * @return `true` when the Setting for allowing Duck.ai chats to be deleted with the Fire Button is enabled
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun clearHistory(): Toggle

    /**
     * @return `true` when the new input screen should show the three main buttons (fire, tabs, menu)
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun showMainButtonsInInputScreen(): Toggle
}
