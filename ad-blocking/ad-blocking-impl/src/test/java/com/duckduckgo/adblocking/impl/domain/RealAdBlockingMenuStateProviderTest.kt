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

package com.duckduckgo.adblocking.impl.domain

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.AdBlockingExtensionDomainMatcher
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealAdBlockingMenuStateProviderTest {

    private val killSwitchFlow = MutableStateFlow(true)
    private val phase2Flow = MutableStateFlow(true)
    private val contingencyFlow = MutableStateFlow(false)
    private val stateFlow = MutableStateFlow<AdBlockingState>(AdBlockingState.Enabled.UserEnabled)

    private val selfToggle: Toggle = mock { on { enabled() } doReturn killSwitchFlow }
    private val phase2Toggle: Toggle = mock { on { enabled() } doReturn phase2Flow }
    private val contingencyToggle: Toggle = mock { on { enabled() } doReturn contingencyFlow }
    private val feature: AdBlockingExtensionFeature = mock {
        on { self() } doReturn selfToggle
        on { adBlockingUXImprovements() } doReturn phase2Toggle
        on { enableContingencyMode() } doReturn contingencyToggle
    }
    private val statusChecker: AdBlockingStatusChecker = mock {
        on { observeState() } doReturn stateFlow
    }

    private val youtubeUrl = "https://m.youtube.com/watch?v=abc".toUri()
    private val nonYoutubeUrl = "https://example.com/watch".toUri()

    private val domainMatcher: AdBlockingExtensionDomainMatcher = mock {
        on { matches(youtubeUrl) } doReturn true
        on { matches(nonYoutubeUrl) } doReturn false
    }

    private val provider = RealAdBlockingMenuStateProvider(feature, statusChecker, domainMatcher)

    @Test
    fun whenNonYoutubeUrlThenHidden() = runTest {
        assertEquals(AdBlockingMenuState.Hidden, provider.observe(nonYoutubeUrl).first())
    }

    @Test
    fun whenPhase2FlagOffThenHidden() = runTest {
        phase2Flow.value = false

        assertEquals(AdBlockingMenuState.Hidden, provider.observe(youtubeUrl).first())
    }

    @Test
    fun whenKillSwitchOffThenHidden() = runTest {
        killSwitchFlow.value = false

        assertEquals(AdBlockingMenuState.Hidden, provider.observe(youtubeUrl).first())
    }

    @Test
    fun whenContingencyModeOnThenHidden() = runTest {
        contingencyFlow.value = true

        assertEquals(AdBlockingMenuState.Hidden, provider.observe(youtubeUrl).first())
    }

    @Test
    fun whenYoutubeAndUserEnabledThenEnabled() = runTest {
        stateFlow.value = AdBlockingState.Enabled.UserEnabled

        assertEquals(AdBlockingMenuState.Enabled, provider.observe(youtubeUrl).first())
    }

    @Test
    fun whenYoutubeAndEnabledByDefaultThenEnabled() = runTest {
        stateFlow.value = AdBlockingState.Enabled.Default

        assertEquals(AdBlockingMenuState.Enabled, provider.observe(youtubeUrl).first())
    }

    @Test
    fun whenYoutubeAndDisabledThenDisabled() = runTest {
        stateFlow.value = AdBlockingState.Disabled.Permanent

        assertEquals(AdBlockingMenuState.Disabled, provider.observe(youtubeUrl).first())
    }
}
