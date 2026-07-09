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

package com.duckduckgo.app.onboarding.ui.view

import android.content.Context
import android.graphics.Canvas
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class OnboardingFillImageViewTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun view() = OnboardingFillImageView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    @Test
    fun whenFillHeightSetThenScaleTypeIsMatrixAndAdjustViewBoundsDisabled() {
        val view = view()
        view.setFillHeight(280)
        assertEquals(ImageView.ScaleType.MATRIX, view.scaleType)
        assertFalse(view.adjustViewBounds)
        assertEquals(280, view.layoutParams.height)
    }

    @Test
    fun whenFillClearedThenInflatedDefaultsRestored() {
        val view = view().apply {
            scaleType = ImageView.ScaleType.FIT_END
            adjustViewBounds = true
            maxHeight = 126
        }
        view.setFillHeight(280)
        view.clearFill()
        assertEquals(ImageView.ScaleType.FIT_END, view.scaleType)
        assertTrue(view.adjustViewBounds)
        assertEquals(126, view.maxHeight)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.height)
    }

    @Test
    fun whenClearedWithoutEverFillingThenNoOp() {
        val view = view().apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        view.clearFill()
        assertEquals(ImageView.ScaleType.FIT_CENTER, view.scaleType)
    }

    @Test
    fun whenFillingThenOnDrawClipsToViewBounds() {
        val view = exposedView()
        view.setFillHeight(140)
        view.layout(0, 0, 1000, 140)
        val canvas: Canvas = mock()
        view.onDraw(canvas)
        verify(canvas).clipRect(0, 0, 1000, 140)
    }

    @Test
    fun whenNotFillingThenOnDrawDoesNotClip() {
        val view = exposedView()
        view.layout(0, 0, 1000, 140)
        val canvas: Canvas = mock()
        view.onDraw(canvas)
        verify(canvas, never()).clipRect(0, 0, 1000, 140)
    }

    private fun exposedView() = ExposedFillImageView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private class ExposedFillImageView(context: Context) : OnboardingFillImageView(context) {
        public override fun onDraw(canvas: Canvas) = super.onDraw(canvas)
    }
}
