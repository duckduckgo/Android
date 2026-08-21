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

import com.duckduckgo.app.browser.R
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.ui.store.AppBrandDesignUpdateToggles
import com.duckduckgo.common.ui.store.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class BrowserLottieTrackersAnimatorHelperResolveCookieAnimationTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val testee = BrowserLottieTrackersAnimatorHelper(
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        theme = mock<AppTheme>(),
        addressBarTrackersAnimator = mock<AddressBarTrackersAnimator>(),
        commonAddressBarAnimationHelper = mock<CommonAddressBarAnimationHelper>(),
        appBrandDesignUpdateToggles = mock<AppBrandDesignUpdateToggles>(),
    )

    @Test
    fun whenBrandIconsEnabledAndLightThenBrandUpdateLightAnimation() {
        assertEquals(
            R.raw.cookie_icon_animated_light_brand_update,
            testee.resolveCookieAnimation(isLightMode = true, brandIconsEnabled = true),
        )
    }

    @Test
    fun whenBrandIconsEnabledAndDarkThenBrandUpdateDarkAnimation() {
        assertEquals(
            R.raw.cookie_icon_animated_dark_brand_update,
            testee.resolveCookieAnimation(isLightMode = false, brandIconsEnabled = true),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndLightThenLegacyLightAnimation() {
        assertEquals(
            R.raw.cookie_icon_animated_light,
            testee.resolveCookieAnimation(isLightMode = true, brandIconsEnabled = false),
        )
    }

    @Test
    fun whenBrandIconsDisabledAndDarkThenLegacyDarkAnimation() {
        assertEquals(
            R.raw.cookie_icon_animated_dark,
            testee.resolveCookieAnimation(isLightMode = false, brandIconsEnabled = false),
        )
    }
}
