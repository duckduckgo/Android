/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor.ui

import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworksRendererTest {

    private val context = InstrumentationRegistry.getTargetContext()
    private val testee = NetworksRenderer()

    @Test
    fun whenNoTrackerNetworksThenNetworksTextShowsZeroBlocked() {
        val text = testee.networksText(context, 0, true)
        assertEquals("0 Tracker Networks Blocked", text)
    }

    @Test
    fun whenTenTrackerNetworksAndAllBlockedThenNetworksTextShowsTenBlocked() {
        val text = testee.networksText(context, 10, true)
        assertEquals("10 Tracker Networks Blocked", text)
    }

    @Test
    fun whenTenTrackersNetworksAndNotAllBlockedThenNetworksTextShowsTenFound() {
        val text = testee.networksText(context, 10, false)
        assertEquals("10 Tracker Networks Found", text)
    }

    @Test
    fun whenAllTrackersBlockedThenNetworksBannerIsGood() {
        val resource = testee.networksBanner(true)
        assertEquals(R.drawable.networks_banner_good, resource)
    }

    @Test
    fun whenNotAllTrackersBlockedThenNetworksBannerIsBad() {
        val resource = testee.networksBanner(false)
        assertEquals(R.drawable.networks_banner_bad, resource)
    }

    @Test
    fun whenAllTrackersBlockedThenNetworksIconIsGood() {
        val resource = testee.networksIcon(true)
        assertEquals(R.drawable.networks_icon_good, resource)
    }

    @Test
    fun whenNotAllTrackersBlockedThenNetworksIconIsBad() {
        val resource = testee.networksIcon(false)
        assertEquals(R.drawable.networks_icon_bad, resource)
    }
}