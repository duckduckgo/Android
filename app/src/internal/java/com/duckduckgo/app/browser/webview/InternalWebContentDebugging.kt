/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser.webview

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(
    scope = ActivityScope::class,
    replaces = [RealWebContentDebugging::class],
)
class InternalWebContentDebugging @Inject constructor(
    private val webContentDebuggingFeature: WebContentDebuggingFeature,
) : WebContentDebugging {
    override fun isEnabled(): Boolean {
        return webContentDebuggingFeature.webContentDebugging().isEnabled()
    }
}

@ContributesRemoteFeature(
    scope = ActivityScope::class,
    featureName = "InternalWebContentDebuggingFlag",
)
interface WebContentDebuggingFeature {
    @Toggle.DefaultValue(false)
    fun self(): Toggle

    @Toggle.DefaultValue(false)
    fun webContentDebugging(): Toggle
}
