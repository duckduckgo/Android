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

package com.duckduckgo.widget

import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchWidgetConfiguratorResolveSearchBarBackgroundTest {

    @Test
    fun whenAddressBarRebrandEnabledThenEachThemeUsesRebrandBackground() {
        assertEquals(
            R.drawable.search_widget_background_rebrand_light,
            resolveSearchBarBackground(WidgetTheme.LIGHT, isAddressBarRebrandEnabled = true),
        )
        assertEquals(
            R.drawable.search_widget_background_rebrand_dark,
            resolveSearchBarBackground(WidgetTheme.DARK, isAddressBarRebrandEnabled = true),
        )
        assertEquals(
            R.drawable.search_widget_background_rebrand_daynight,
            resolveSearchBarBackground(WidgetTheme.SYSTEM_DEFAULT, isAddressBarRebrandEnabled = true),
        )
    }

    @Test
    fun whenAddressBarRebrandDisabledThenEachThemeUsesLegacyBackground() {
        assertEquals(
            R.drawable.search_widget_background_light,
            resolveSearchBarBackground(WidgetTheme.LIGHT, isAddressBarRebrandEnabled = false),
        )
        assertEquals(
            R.drawable.search_widget_background_dark,
            resolveSearchBarBackground(WidgetTheme.DARK, isAddressBarRebrandEnabled = false),
        )
        assertEquals(
            R.drawable.search_widget_background_daynight,
            resolveSearchBarBackground(WidgetTheme.SYSTEM_DEFAULT, isAddressBarRebrandEnabled = false),
        )
    }
}
