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

package com.duckduckgo.app.browser.omnibar

import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OmnibarDuckPlayerIconTest {

    @Test
    fun whenAddressBarRebrandIsEnabledThenDuckPlayerUsesRebrandIcon() {
        assertEquals(
            R.drawable.video_player_color_24_brand_update,
            resolveDuckPlayerIcon(isAddressBarRebrandEnabled = true),
        )
    }

    @Test
    fun whenAddressBarRebrandIsDisabledThenDuckPlayerUsesLegacyIcon() {
        assertEquals(
            R.drawable.ic_video_player_color_24,
            resolveDuckPlayerIcon(isAddressBarRebrandEnabled = false),
        )
    }
}
