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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "pageLoad",
)
interface PageLoadFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /** When off, the page load timeout watchdog is never armed: no timeout detection, no pixels, no error. */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun pageLoadTimeoutWatchdog(): Toggle

    /** When off, a detected timeout is only reported through pixels and the timeout error is never shown. */
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun pageLoadTimeoutError(): Toggle
}
