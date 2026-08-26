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

package com.duckduckgo.app.browser.ui.dialogs.widgetprompt

import com.duckduckgo.app.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenWidgetBottomSheetDialogResolveWidgetPromoAssetTest {

    @Test
    fun whenAddressBarEnabledAndLightThenBrandUpdateAsset() {
        assertEquals(
            R.drawable.widget_promo_light_brand_update,
            resolveWidgetPromoAsset(isAddressBarRebrandEnabled = true, isLightModeEnabled = true),
        )
    }

    @Test
    fun whenAddressBarEnabledAndDarkThenBrandUpdateAsset() {
        assertEquals(
            R.drawable.widget_promo_dark_brand_update,
            resolveWidgetPromoAsset(isAddressBarRebrandEnabled = true, isLightModeEnabled = false),
        )
    }

    @Test
    fun whenAddressBarDisabledAndLightThenLegacyAsset() {
        assertEquals(
            R.drawable.widget_promo_light,
            resolveWidgetPromoAsset(isAddressBarRebrandEnabled = false, isLightModeEnabled = true),
        )
    }

    @Test
    fun whenAddressBarDisabledAndDarkThenLegacyAsset() {
        assertEquals(
            R.drawable.widget_promo_dark,
            resolveWidgetPromoAsset(isAddressBarRebrandEnabled = false, isLightModeEnabled = false),
        )
    }
}
