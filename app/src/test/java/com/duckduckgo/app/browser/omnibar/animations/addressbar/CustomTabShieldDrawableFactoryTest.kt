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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.duckduckgo.app.browser.R
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.R as CommonR

class CustomTabShieldDrawableFactoryTest {

    private val context: Context = mock()
    private val resources: Resources = mock()
    private val baseTheme: Resources.Theme = mock()
    private val drawableTheme: Resources.Theme = mock()
    private val drawable: Drawable = mock()
    private val testee = CustomTabShieldDrawableFactory()

    @Test
    fun whenToolbarIsLightThenShieldUsesDuckDuckGoLightTheme() {
        givenDrawableInflation()

        testee.create(context, R.drawable.shield_alert_24, isLightMode = true)

        verify(drawableTheme).applyStyle(CommonR.style.Theme_DuckDuckGo_Light, true)
    }

    @Test
    fun whenToolbarIsDarkThenShieldUsesDuckDuckGoDarkTheme() {
        givenDrawableInflation()

        testee.create(context, R.drawable.shield_alert_24, isLightMode = false)

        verify(drawableTheme).applyStyle(CommonR.style.Theme_DuckDuckGo_Dark, true)
    }

    @Suppress("DEPRECATION")
    private fun givenDrawableInflation() {
        whenever(context.resources).thenReturn(resources)
        whenever(context.theme).thenReturn(baseTheme)
        whenever(resources.newTheme()).thenReturn(drawableTheme)
        whenever(resources.getDrawable(eq(R.drawable.shield_alert_24), eq(drawableTheme))).thenReturn(drawable)
        whenever(resources.getDrawable(any<Int>())).thenReturn(drawable)
    }
}
