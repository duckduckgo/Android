/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.animations

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.animations.addressbar.TrackersRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackersRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testee = TrackersRenderer()

    @Test
    fun whenNetworkNameMatchesLogoIconThenResourceIsReturned() {
        val resource = testee.networkLogoIcon(context, "outbrain")
        assertEquals(R.drawable.network_logo_outbrain, resource)
    }

    @Test
    fun whenNetworkNameSansSpecialCharactersAndWithUnderscoresForSpacesMatchesLogoIconThenResourceIsReturned() {
        val resource = testee.networkLogoIcon(context, "Amazon Technologies, Inc.")
        assertEquals(R.drawable.network_logo_amazon_technologies_inc, resource)
    }
}
