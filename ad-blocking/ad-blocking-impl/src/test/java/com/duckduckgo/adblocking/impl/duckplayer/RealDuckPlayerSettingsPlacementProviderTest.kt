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

package com.duckduckgo.adblocking.impl.duckplayer

import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.DISABLED_WIH_HELP_LINK
import com.duckduckgo.adblocking.api.duckplayer.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsPlacement.Hidden
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsPlacement.InAdBlocking
import com.duckduckgo.adblocking.impl.duckplayer.DuckPlayerSettingsPlacement.Standalone
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@RunWith(Parameterized::class)
class RealDuckPlayerSettingsPlacementProviderTest(private val testCase: TestCase) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): List<TestCase> = listOf(
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = true, ENABLED, expected = InAdBlocking),
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = true, DISABLED_WIH_HELP_LINK, expected = InAdBlocking),
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = true, DISABLED, expected = Hidden),
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = false, ENABLED, expected = Standalone),
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = false, DISABLED_WIH_HELP_LINK, expected = Standalone),
            TestCase(standaloneSettings = false, adBlockingSettingsVisible = false, DISABLED, expected = Hidden),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = true, ENABLED, expected = Standalone),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = true, DISABLED_WIH_HELP_LINK, expected = Standalone),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = true, DISABLED, expected = Hidden),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = false, ENABLED, expected = Standalone),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = false, DISABLED_WIH_HELP_LINK, expected = Standalone),
            TestCase(standaloneSettings = true, adBlockingSettingsVisible = false, DISABLED, expected = Hidden),
        )
    }

    @Test
    fun placementMatchesExpected() = runTest {
        val duckPlayer: DuckPlayerInternal = mock {
            on { observeDuckPlayerState() } doReturn flowOf(testCase.duckPlayerState)
        }
        val standaloneSettingsToggle: Toggle = mock {
            on { enabled() } doReturn flowOf(testCase.standaloneSettings)
        }
        val duckPlayerFeature: DuckPlayerFeature = mock {
            on { standaloneSettings() } doReturn standaloneSettingsToggle
        }
        val statusChecker: AdBlockingStatusChecker = mock {
            on { observeSettingsVisible() } doReturn flowOf(testCase.adBlockingSettingsVisible)
        }
        val provider = RealDuckPlayerSettingsPlacementProvider(duckPlayer, duckPlayerFeature, statusChecker)

        assertEquals(testCase.expected, provider.placementFlow().first())
    }

    data class TestCase(
        val standaloneSettings: Boolean,
        val adBlockingSettingsVisible: Boolean,
        val duckPlayerState: DuckPlayerState,
        val expected: DuckPlayerSettingsPlacement,
    )
}
