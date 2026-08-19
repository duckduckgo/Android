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

package com.duckduckgo.duckchat.impl.metric.nativeinput

import com.duckduckgo.duckchat.impl.metric.nativeinput.discovery.InputScreenDiscoveryFunnel
import com.duckduckgo.duckchat.impl.metric.nativeinput.usage.InputScreenSessionUsageMetric
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class MetricsNativeInputEventListenerTest {

    private val duckChatPixels: DuckChatPixels = mock()
    private val sessionUsageMetric: InputScreenSessionUsageMetric = mock()
    private val discoveryFunnel: InputScreenDiscoveryFunnel = mock()

    private val testee = MetricsNativeInputEventListener(
        duckChatPixels = duckChatPixels,
        sessionUsageMetric = sessionUsageMetric,
        discoveryFunnel = discoveryFunnel,
    )

    @Test
    fun whenSearchSubmittedThenUsageAndDiscoveryMetricsAreUpdated() {
        testee.onSearchSubmitted("query")

        verify(sessionUsageMetric).onSearchSubmitted()
        verify(discoveryFunnel).onSearchSubmitted()
        verifyNoInteractions(duckChatPixels)
    }
}
