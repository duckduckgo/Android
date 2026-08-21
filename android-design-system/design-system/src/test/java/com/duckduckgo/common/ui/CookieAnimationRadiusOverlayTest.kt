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

package com.duckduckgo.common.ui

import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class CookieAnimationRadiusOverlayTest {

    @Test
    fun whenAddressBarRebrandIsDisabledThenGeneralRebrandOverlayKeepsLegacyCookieRadius() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuckDuckGo_Light)
        context.theme.applyStyle(R.style.ThemeOverlay_Rebrand, true)

        assertEquals(
            context.resources.getDimension(R.dimen.cookiesAnimationRadius),
            resolveDimension(context, R.attr.cookiesAnimationRadius),
            0f,
        )
    }

    @Test
    fun whenAddressBarRebrandIsEnabledThenAddressBarAnimationOverlayUsesRebrandCookieRadius() {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuckDuckGo_Light)
        context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_AddressBarAnimation, true)

        assertEquals(
            context.resources.getDimension(R.dimen.rebrandInputRadius),
            resolveDimension(context, R.attr.cookiesAnimationRadius),
            0f,
        )
    }

    private fun resolveDimension(
        context: ContextThemeWrapper,
        attr: Int,
    ): Float {
        val value = TypedValue()
        check(context.theme.resolveAttribute(attr, value, true))
        return context.resources.getDimension(value.resourceId)
    }
}
