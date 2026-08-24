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

package com.duckduckgo.autoconsent.impl.prompt

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autoconsent.impl.databinding.ActivityCookiePopupOptInBinding
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The screen stacks three theme sources: the layout root's onboarding overlay for the ?attr/onboarding*
 * sheet and bubble colors, the base theme for the brand button, and ThemeOverlay.Rebrand on the
 * secondary button alone for its filled variant. Inflating here is what catches one of them not
 * resolving.
 */
@RunWith(AndroidJUnit4::class)
class CookiePopupOptInLayoutTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), CommonR.style.Theme_DuckDuckGo_Light)

    @Test
    fun whenInflatedThenSheetAndBubbleResolveOnboardingBackgrounds() {
        val binding = ActivityCookiePopupOptInBinding.inflate(LayoutInflater.from(context))

        assertNotNull(binding.cookiePopupOptInSheet.background)
        assertNotNull(binding.cookiePopupOptInBubble.background)
    }

    @Test
    fun whenInflatedThenBothButtonsResolveTheirBackgrounds() {
        val binding = ActivityCookiePopupOptInBinding.inflate(LayoutInflater.from(context))

        assertNotNull(binding.cookiePopupOptInAcceptButton.background)
        assertNotNull(binding.cookiePopupOptInDeclineButton.background)
    }
}
