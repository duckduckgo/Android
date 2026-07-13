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

package com.duckduckgo.adblocking.impl

import android.annotation.SuppressLint
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class AdBlockingContextualCtaSuppressorPluginTest {

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val domainMatcher: AdBlockingExtensionDomainMatcher = mock()
    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)

    private val plugin = AdBlockingContextualCtaSuppressorPlugin(statusChecker, feature, domainMatcher)

    private val url = "https://www.youtube.com/watch?v=123".toUri()

    @Test
    fun whenPhase2DisabledThenCanShowCta() = runTest {
        setPhase2(false)

        assertTrue(plugin.canShowCta(url))
    }

    @Test
    fun whenAdBlockingNotActiveThenCanShowCta() = runTest {
        setPhase2(true)
        whenever(statusChecker.canInject()).thenReturn(false)

        assertTrue(plugin.canShowCta(url))
    }

    @Test
    fun whenPhase2AndAdBlockingActiveOnNonMatchingDomainThenCanShowCta() = runTest {
        setPhase2(true)
        whenever(statusChecker.canInject()).thenReturn(true)
        whenever(domainMatcher.matches(any<Uri>())).thenReturn(false)

        assertTrue(plugin.canShowCta(url))
    }

    @Test
    fun whenPhase2AndAdBlockingActiveOnMatchingDomainThenSuppress() = runTest {
        setPhase2(true)
        whenever(statusChecker.canInject()).thenReturn(true)
        whenever(domainMatcher.matches(any<Uri>())).thenReturn(true)

        assertFalse(plugin.canShowCta(url))
    }

    private fun setPhase2(enabled: Boolean) {
        feature.adBlockingUXImprovements().setRawStoredState(Toggle.State(remoteEnableState = enabled))
    }
}
