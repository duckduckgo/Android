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

package com.duckduckgo.browser.ui.autocomplete

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Kill switch for the explicit "X" delete button on history autocomplete suggestions.
 *
 * When enabled, history suggestions (sites and past searches) show an X button that deletes the
 * item. When disabled, the previous behaviour is restored: no X button, and the item is deleted
 * via a (hidden) long press.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "autocompleteHistoryDelete",
)
interface AutocompleteHistoryDeleteFeature {
    /**
     * @return `true` when the autocomplete history delete button is enabled. Defaults to `true`.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
