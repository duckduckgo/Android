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
import android.graphics.drawable.Drawable
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.global.model.PrivacyShield.MALICIOUS
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.store.AppTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperResolveShieldAssetTest {

    private val customTabShieldDrawableFactory = mock<CustomTabShieldDrawableFactory>()
    private val testee = LottiePrivacyShieldAnimationHelper(
        appTheme = mock<AppTheme>(),
        customTabShieldDrawableFactory = customTabShieldDrawableFactory,
    )

    @Test
    fun whenProtectedThenColorShield() {
        assertEquals(R.raw.shield_color_24, testee.resolveShieldAsset(PROTECTED))
    }

    @Test
    fun whenUnprotectedThenAlertShield() {
        assertEquals(R.drawable.shield_alert_24, testee.resolveShieldAsset(UNPROTECTED))
    }

    @Test
    fun whenMaliciousThenStaticExclamation() {
        assertEquals(R.drawable.exclamation_recolorable_24, testee.resolveShieldAsset(MALICIOUS))
    }

    @Test
    fun whenUnknownThenNoAsset() {
        assertNull(testee.resolveShieldAsset(UNKNOWN))
    }

    @Test
    fun whenLightCustomTabThenDoNotTintWholeStaticShield() = runTest {
        val holder: LottieAnimationView = mock()
        val context: Context = mock()
        val drawable: Drawable = mock()
        whenever(holder.context).thenReturn(context)
        whenever(customTabShieldDrawableFactory.create(any(), eq(R.drawable.shield_alert_24), eq(true))).thenReturn(drawable)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.CustomTab(0, "example.com", "example.com"),
            useLightAnimation = true,
        )

        verify(holder).setImageDrawable(drawable)
    }

    @Test
    fun whenDarkCustomTabThenDoNotTintWholeStaticShield() = runTest {
        val holder: LottieAnimationView = mock()
        val context: Context = mock()
        val drawable: Drawable = mock()
        whenever(holder.context).thenReturn(context)
        whenever(customTabShieldDrawableFactory.create(any(), eq(R.drawable.shield_alert_24), eq(false))).thenReturn(drawable)

        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.CustomTab(0, "example.com", "example.com"),
            useLightAnimation = false,
        )

        verify(holder).setImageDrawable(drawable)
    }

    @Test
    fun whenNormalBrowserThenUseStaticShieldWithoutCustomTabTint() = runTest {
        val holder: LottieAnimationView = mock()
        testee.setAnimationView(
            holder = holder,
            privacyShield = UNPROTECTED,
            viewMode = ViewMode.Browser("example.com"),
            useLightAnimation = false,
        )

        verify(holder).setImageResource(R.drawable.shield_alert_24)
    }
}
