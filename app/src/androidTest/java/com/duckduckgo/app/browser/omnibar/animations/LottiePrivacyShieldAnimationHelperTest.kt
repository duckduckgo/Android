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

import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.animations.addressbar.CustomTabShieldDrawableFactory
import com.duckduckgo.app.browser.omnibar.animations.addressbar.LottiePrivacyShieldAnimationHelper
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.common.ui.store.AppTheme
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LottiePrivacyShieldAnimationHelperTest {

    private val browserViewMode = Omnibar.ViewMode.Browser("cnn.com")
    private val customTabShieldDrawableFactory = CustomTabShieldDrawableFactory()

    @Test
    fun whenPrivacyShieldProtectedThenUseColorShieldAnimation() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        whenever(appTheme.isLightModeEnabled()).thenReturn(true)
        val testee = createTestee(appTheme)

        testee.setAnimationView(holder, PROTECTED, browserViewMode)

        verify(holder).setAnimation(R.raw.shield_color_24)
    }

    @Test
    fun whenPrivacyShieldUnprotectedThenUseAlertDrawable() = runTest {
        val holder: LottieAnimationView = mock()
        val appTheme: AppTheme = mock()
        val testee = createTestee(appTheme)

        testee.setAnimationView(holder, UNPROTECTED, browserViewMode)

        verify(holder).setImageResource(R.drawable.shield_alert_24)
    }

    private fun createTestee(appTheme: AppTheme): LottiePrivacyShieldAnimationHelper = LottiePrivacyShieldAnimationHelper(
        appTheme,
        customTabShieldDrawableFactory,
    )
}
