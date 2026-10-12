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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.graphics.Outline
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ModelPickerViewTest {

    @Test
    fun whenRadiusIsDisabledThenPopupKeepsUnclipped8DpCorners() {
        assertPopupClipping(radiusEnabled = false, expectedRadiusDp = 8f)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun whenRadiusIsEnabledThenPopupClipsChildrenTo16DpCorners() {
        assertPopupClipping(radiusEnabled = true, expectedRadiusDp = 16f)
    }

    private fun assertPopupClipping(radiusEnabled: Boolean, expectedRadiusDp: Float) {
        listOf(CommonR.style.Theme_DuckDuckGo_Light, CommonR.style.Theme_DuckDuckGo_Dark).forEach { themeResId ->
            val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), themeResId)
            if (radiusEnabled) context.theme.applyStyle(CommonR.style.ThemeOverlay_Rebrand_Radius, true)
            val picker = ModelPickerView(context)
            // Exercise the production popup without attaching the unrelated injected view model.
            val rows = ModelPickerView::class.java.getDeclaredMethod("buildMenuContainer")
                .apply { isAccessible = true }.invoke(picker) as LinearLayout
            val popup = ModelPickerView::class.java.getDeclaredMethod("createPopupWindow", LinearLayout::class.java)
                .apply { isAccessible = true }.invoke(picker, rows) as PopupWindow
            val viewport = popup.contentView
            viewport.layout(0, 0, 240, 120)
            viewport.background.setBounds(0, 0, 240, 120)
            val outline = Outline().also { viewport.outlineProvider.getOutline(viewport, it) }

            assertEquals(expectedRadiusDp * context.resources.displayMetrics.density, outline.radius, 0f)
            assertEquals("The rounded viewport must own clipping", radiusEnabled, viewport.clipToOutline)
        }
    }
}
