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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.NativeInputEventListener
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.usage.InputScreenSessionUsageMetric
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class MetricsNativeInputEventListener @Inject constructor(
    private val duckChatPixels: DuckChatPixels,
    private val sessionUsageMetric: InputScreenSessionUsageMetric,
    private val discoveryFunnel: InputScreenDiscoveryFunnel,
) : NativeInputEventListener {

    override fun onNativeInputShown(landscape: Boolean) {
        discoveryFunnel.onNativeInputActive()
        discoveryFunnel.onInputScreenOpened()
        duckChatPixels.fireOmnibarShown()
        duckChatPixels.fireOmnibarTextAreaFocused(landscape = landscape)
    }

    override fun onSearchSubmitted(query: String) {
        duckChatPixels.fireOmnibarQuerySubmitted(query)
        sessionUsageMetric.onSearchSubmitted()
        discoveryFunnel.onSearchSubmitted()
    }

    override fun onChatPromptSubmitted() {
        sessionUsageMetric.onPromptSubmitted()
        discoveryFunnel.onPromptSubmitted()
    }
}
