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

package com.duckduckgo.remote.messaging.impl

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class WhatsNewCardRadiusTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenBothWhatsNewCardsKeep12DpCorners() {
        assertCardRadii(overlayEnabled = false, expectedDp = 12f)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenBothWhatsNewCardsUse28DpCorners() {
        assertCardRadii(overlayEnabled = true, expectedDp = 28f)
    }

    private fun assertCardRadii(overlayEnabled: Boolean, expectedDp: Float) {
        listOf(CommonR.style.Theme_DuckDuckGo_Light, CommonR.style.Theme_DuckDuckGo_Dark).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (overlayEnabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_Radius, true)
            val expectedRadius = expectedDp * context.resources.displayMetrics.density

            val ripple = checkNotNull(ContextCompat.getDrawable(context, R.drawable.background_remote_message_entry)) as RippleDrawable
            val regularCardShape = ripple.getDrawable(0) as GradientDrawable
            assertEquals(expectedRadius, regularCardShape.getCornerRadius(), 0f)

            val featuredRow = LayoutInflater.from(context).inflate(R.layout.view_remote_message_featured_list_item, null) as ViewGroup
            val featuredCard = featuredRow.getChildAt(0) as MaterialCardView
            assertEquals(expectedRadius, featuredCard.radius, 0f)
        }
    }
}
