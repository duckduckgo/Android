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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.graphics.Outline
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomShadowTest {

    private val context = ApplicationProvider.getApplicationContext<Context>().apply {
        setTheme(R.style.Theme_DuckDuckGo_Light)
    }

    @Test
    fun whenMaterialCardHasStaticRadiusThenBottomShadowUsesMaterialCardRadius() {
        val card = RadiusReportingMaterialCardView(context).apply {
            outlineRadius = 7f
            shapeAppearanceModel = shapeAppearanceModel.withCornerSize(18f)
            layout(0, 0, 100, 40)
            addBottomShadow()
        }

        assertEquals(7f, card.bottomShadowOutline().radius, 0f)
    }

    @Test
    fun whenMaterialCardRadiusChangesAfterAddingBottomShadowThenOutlineUsesUpdatedRadius() {
        val card = MaterialCardView(context).apply {
            shapeAppearanceModel = shapeAppearanceModel.withCornerSize(7f)
            layout(0, 0, 100, 40)
            addBottomShadow()
        }

        card.shapeAppearanceModel = card.shapeAppearanceModel.withCornerSize(16f)

        assertEquals(16f, card.radius, 0f)
        assertEquals(16f, card.bottomShadowOutline().radius, 0f)
    }

    @Test
    fun whenViewIsNotMaterialCardThenBottomShadowUsesSquareOutline() {
        val view = View(context).apply {
            layout(0, 0, 100, 40)
            addBottomShadow()
        }

        assertEquals(0f, view.bottomShadowOutline().radius, 0f)
    }

    private fun View.bottomShadowOutline(): Outline = Outline().also { outline ->
        outlineProvider.getOutline(this, outline)
    }

    private class RadiusReportingMaterialCardView(context: Context) : MaterialCardView(context) {
        var outlineRadius: Float = 0f

        override fun getRadius(): Float = outlineRadius
    }
}
