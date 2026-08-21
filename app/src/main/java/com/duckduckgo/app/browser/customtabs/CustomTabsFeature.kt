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

package com.duckduckgo.app.browser.customtabs

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "customTabs",
)
interface CustomTabsFeature {

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /**
     * When enabled, app links in a custom tab honor the "Open Links in Apps" setting except when the link
     * returns to the app that opened the tab (a trusted caller) or is an always-trigger domain. When disabled,
     * the legacy behavior applies: every app link in a custom tab opens directly, without prompting.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun handleTrustedCallers(): Toggle

    /**
     * When enabled, a custom tab is closed after handing off a trusted-caller navigation (e.g. an
     * OAuth/login flow returning to the app that opened the tab), so the user doesn't return to a stale tab.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun closeTabAfterTrustedCallerNavigation(): Toggle
}
