/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 ( the "License" );
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.common.ui

import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MessageCtaCloseButtonInsetTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenCloseIconIs8DpFromTopAndEnd() {
        assertCloseIconInset(overlayEnabled = false, expectedInsetDp = 8)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenCloseIconIs12DpFromTopAndEnd() {
        assertCloseIconInset(overlayEnabled = true, expectedInsetDp = 12)
    }

    @Test
    fun whenRadiusOverlayIsAbsentThenCloseButtonDoesNotUseRebrandRippleInset() {
        assertCloseButtonUsesInsetCircularRipple(overlayEnabled = false, expectedRebrandRipple = false)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusOverlayIsAppliedThenCloseButtonRippleFitsInsideCardCorner() {
        assertCloseButtonUsesInsetCircularRipple(overlayEnabled = true, expectedRebrandRipple = true)
    }

    @Test
    fun whenRadiusOverlayIsAbsentThenRemoteMessageContentClipsToPadding() {
        assertRemoteMessageContentClipsToPadding(overlayEnabled = false, expectedClipToPadding = true)
    }

    @Test
    fun whenRadiusOverlayIsAppliedThenRemoteMessageContentDrawsIntoPadding() {
        assertRemoteMessageContentClipsToPadding(overlayEnabled = true, expectedClipToPadding = false)
    }

    private fun assertCloseIconInset(
        overlayEnabled: Boolean,
        expectedInsetDp: Int,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            listOf(
                R.layout.view_remote_message_cta,
                R.layout.view_promo_message_cta,
            ).forEach { layoutResId ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
                if (overlayEnabled) {
                    context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
                }
                val card = LayoutInflater.from(context).inflate(layoutResId, null) as ViewGroup
                val container = card.getChildAt(0) as ViewGroup
                val close = card.findViewById<ImageView>(R.id.close)
                val layoutParams = close.layoutParams as ViewGroup.MarginLayoutParams
                val expectedInset = expectedInsetDp * context.resources.displayMetrics.density
                val centeredDrawableInset = (layoutParams.width - close.drawable.intrinsicWidth) / 2f

                assertEquals(ImageView.ScaleType.CENTER, close.scaleType)
                assertEquals(
                    expectedInset,
                    container.paddingTop + layoutParams.topMargin + centeredDrawableInset,
                    0f,
                )
                assertEquals(
                    expectedInset,
                    container.paddingEnd + layoutParams.marginEnd + centeredDrawableInset,
                    0f,
                )
            }
        }
    }

    private fun assertCloseButtonUsesInsetCircularRipple(
        overlayEnabled: Boolean,
        expectedRebrandRipple: Boolean,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            listOf(
                R.layout.view_remote_message_cta,
                R.layout.view_promo_message_cta,
            ).forEach { layoutResId ->
                val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
                if (overlayEnabled) {
                    context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
                }
                val card = LayoutInflater.from(context).inflate(layoutResId, null) as ViewGroup
                val close = card.findViewById<ImageView>(R.id.close)
                val inset = close.background as? InsetDrawable
                inset?.bounds = Rect(0, 0, close.layoutParams.width, close.layoutParams.height)
                val ripple = inset?.drawable as? RippleDrawable
                val mask = ripple?.findDrawableByLayerId(android.R.id.mask)
                val hasCircularMask = mask is GradientDrawable && mask.shape == GradientDrawable.OVAL
                val expectedInset = (4 * context.resources.displayMetrics.density).toInt()
                val hasExpectedInsets = ripple?.bounds == Rect(
                    expectedInset,
                    expectedInset,
                    close.layoutParams.width - expectedInset,
                    close.layoutParams.height - expectedInset,
                )

                assertEquals(expectedRebrandRipple, hasCircularMask && hasExpectedInsets)
            }
        }
    }

    private fun assertRemoteMessageContentClipsToPadding(
        overlayEnabled: Boolean,
        expectedClipToPadding: Boolean,
    ) {
        listOf(
            R.style.Theme_DuckDuckGo_Light,
            R.style.Theme_DuckDuckGo_Dark,
        ).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (overlayEnabled) {
                context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
            }
            val card = LayoutInflater.from(context).inflate(R.layout.view_remote_message_cta, null) as ViewGroup
            val container = card.getChildAt(0) as ViewGroup

            assertEquals(expectedClipToPadding, container.clipToPadding)
        }
    }
}
