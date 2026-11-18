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

package com.duckduckgo.adblocking.impl.sponsorblock

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class RealSponsorBlockSettingsProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(SponsorBlockFeature::class.java)
    private val provider = RealSponsorBlockSettingsProvider(feature, coroutineRule.testDispatcherProvider)

    @Test
    fun whenSettingsAreMissingThenDefaultsAreReturned() = runTest {
        setSettings(null)

        assertEquals(SponsorBlockSettings(), provider.settings())
    }

    @Test
    fun whenSettingsJsonIsMalformedThenDefaultsAreReturned() = runTest {
        setSettings("{ not valid json")

        assertEquals(SponsorBlockSettings(), provider.settings())
    }

    @Test
    fun whenAllSettingsArePresentThenTheyAreReturned() = runTest {
        setSettings(
            """
            {
                "categories": ["sponsor", "selfpromo"],
                "actionTypes": ["skip", "mute"],
                "progressBarSelectors": [".ytp-progress-bar"]
            }
            """.trimIndent(),
        )

        assertEquals(
            SponsorBlockSettings(
                categories = listOf("sponsor", "selfpromo"),
                actionTypes = listOf("skip", "mute"),
            ),
            provider.settings(),
        )
    }

    @Test
    fun whenSomeSettingsAreMissingThenDefaultsFillTheGaps() = runTest {
        setSettings("""{"categories": ["intro"]}""")

        assertEquals(SponsorBlockSettings(categories = listOf("intro")), provider.settings())
    }

    private fun setSettings(json: String?) {
        feature.self().setRawStoredState(Toggle.State(remoteEnableState = true, settings = json))
    }
}
