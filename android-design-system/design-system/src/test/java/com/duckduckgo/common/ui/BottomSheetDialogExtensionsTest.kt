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

import android.graphics.RectF
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import com.google.android.material.R as MaterialR

@RunWith(AndroidJUnit4::class)
class BottomSheetDialogExtensionsTest {

    @Test
    fun whenRadiusOverlayIsAbsentThenSetRoundCornersUsesTheLegacy12DpRadius() {
        val dialog = themedDialog(radiusOverlayEnabled = false)

        assertTopCornerRadius(
            dialog,
            RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.dialogBorderRadius),
        )
    }

    @Test
    fun whenRadiusOverlayIsAppliedThenSetRoundCornersUsesTheRebrand28DpRadius() {
        val dialog = themedDialog(radiusOverlayEnabled = true)

        assertTopCornerRadius(
            dialog,
            RuntimeEnvironment.getApplication().resources.getDimension(R.dimen.rebrandContainerRadius),
        )
    }

    private fun themedDialog(radiusOverlayEnabled: Boolean): BottomSheetDialog {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_DuckDuckGo_Light)
        if (radiusOverlayEnabled) {
            context.theme.applyStyle(R.style.ThemeOverlay_Rebrand_Radius, true)
        }
        return BottomSheetDialog(context, R.style.Widget_DuckDuckGo_BottomSheetDialog).apply {
            setContentView(FrameLayout(context))
            setRoundCorners()
        }
    }

    private fun assertTopCornerRadius(
        dialog: BottomSheetDialog,
        expected: Float,
    ) {
        val bottomSheet = checkNotNull(dialog.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet))
        val shapeDrawable = checkNotNull(bottomSheet.background as? MaterialShapeDrawable)
        val shape = shapeDrawable.shapeAppearanceModel
        val bounds = RectF()

        assertEquals(expected, shape.topLeftCornerSize.getCornerSize(bounds), 0f)
        assertEquals(expected, shape.topRightCornerSize.getCornerSize(bounds), 0f)
    }
}
