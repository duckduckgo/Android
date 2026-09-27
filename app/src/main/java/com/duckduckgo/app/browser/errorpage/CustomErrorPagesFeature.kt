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

package com.duckduckgo.app.browser.errorpage

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "customErrorPages",
)
interface CustomErrorPagesFeature {
    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    /**
     * Keeps a custom error page (BAD_URL, CONNECTION, SSL_PROTOCOL_ERROR) on screen when the user navigates away
     * from it until a new page starts loading. Otherwise, the old behaviour is dismissing it at submit time, which
     * exposes the WebView's own error page drawn behind the custom error page until the new page commits.
     */
    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.INTERNAL)
    fun keepErrorPageUntilNextPageStartsLoading(): Toggle
}
