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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SponsorBlockConfigSubscriptionEventPluginTest {

    private val sponsorBlock: SponsorBlock = mock()
    private val plugin = SponsorBlockConfigSubscriptionEventPlugin(sponsorBlock)

    @Test
    fun sendsTheCurrentConfigAsOnConfigChanged() {
        whenever(sponsorBlock.config()).thenReturn(SponsorBlockConfig(enabled = true, skipMode = SponsorBlockSkipMode.MANUAL))

        with(plugin.getSubscriptionEventData()) {
            assertEquals("sponsorBlock", featureName)
            assertEquals("onConfigChanged", subscriptionName)
            assertTrue(params.getBoolean("enabled"))
            assertEquals("manual", params.getString("skipMode"))
            assertEquals(1, params.getInt("version"))
        }
    }

    @Test
    fun whenAutoSkipModeThenSendsAuto() {
        whenever(sponsorBlock.config()).thenReturn(SponsorBlockConfig(enabled = true, skipMode = SponsorBlockSkipMode.AUTO))

        assertEquals("auto", plugin.getSubscriptionEventData().params.getString("skipMode"))
    }

    @Test
    fun whenDisabledThenSendsNotEnabled() {
        whenever(sponsorBlock.config()).thenReturn(SponsorBlockConfig(enabled = false, skipMode = SponsorBlockSkipMode.MANUAL))

        assertFalse(plugin.getSubscriptionEventData().params.getBoolean("enabled"))
    }
}
