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

package com.duckduckgo.app.onboarding.ui.page

import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class OnboardingBackgroundAnimatorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var primary: ImageView
    private lateinit var secondary: ImageView
    private lateinit var animator: OnboardingBackgroundAnimator

    @Before
    fun setUp() {
        val parent = ConstraintLayout(context)

        primary = ImageView(context).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        secondary = ImageView(context).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        parent.addView(primary)
        parent.addView(secondary)

        animator = OnboardingBackgroundAnimator(primary, secondary)
    }

    @Test
    fun whenSnapToCalledThenEnteringViewIsVisibleWithFullAlpha() {
        animator.snapTo(OnboardingBackgroundStep.Welcome)

        // Secondary becomes the entering view (active was primary initially)
        assertTrue(secondary.isVisible)
        assertEquals(1f, secondary.alpha)
        assertEquals(0f, secondary.translationX)
    }

    @Test
    fun whenSnapToCalledThenExitingViewIsHidden() {
        animator.snapTo(OnboardingBackgroundStep.Welcome)

        // Primary was active, so it becomes the exiting view
        assertEquals(0f, primary.alpha)
        assertFalse(primary.isVisible)
    }

    @Test
    fun whenSnapToCalledThenMaxHeightIsSetOnEnteringView() {
        animator.snapTo(OnboardingBackgroundStep.Welcome)

        val params = secondary.layoutParams as ConstraintLayout.LayoutParams
        val expectedHeight = (OnboardingBackgroundStep.Welcome.maxHeightDp * context.resources.displayMetrics.density).toInt()
        assertEquals(expectedHeight, params.matchConstraintMaxHeight)
    }

    @Test
    fun whenCancelCalledThenAnimationsAreStopped() {
        // Should not throw even when no animations are running
        animator.cancel()
    }

    @Test
    fun whenSnapToCalledTwiceThenViewsPingPong() {
        animator.snapTo(OnboardingBackgroundStep.Welcome)

        // After first snap: secondary is active
        assertTrue(secondary.isVisible)
        assertFalse(primary.isVisible)

        animator.snapTo(OnboardingBackgroundStep.Welcome)

        // After second snap: primary is active again
        assertTrue(primary.isVisible)
        assertFalse(secondary.isVisible)
    }

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE
}
