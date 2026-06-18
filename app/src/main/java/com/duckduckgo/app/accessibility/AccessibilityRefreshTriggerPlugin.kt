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

package com.duckduckgo.app.accessibility

import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.browser.api.BrowserRefreshTriggerPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Reloads the active tab when accessibility settings (text size / forced zoom) change, so the page
 * re-runs layout with the new zoom instead of clipping. settingsFlow() emits the current values on
 * subscription and a distinct value on each change, so dropping the first emission leaves only the
 * changes that require a reflow.
 */
@ContributesMultibinding(AppScope::class)
class AccessibilityRefreshTriggerPlugin @Inject constructor(
    private val accessibilitySettings: AccessibilitySettingsDataStore,
) : BrowserRefreshTriggerPlugin {

    override fun observeRefreshRequests(): Flow<Unit> =
        accessibilitySettings.settingsFlow()
            .drop(1)
            .map { }
}
