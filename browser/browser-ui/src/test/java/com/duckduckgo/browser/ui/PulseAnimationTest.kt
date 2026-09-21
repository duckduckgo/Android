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

package com.duckduckgo.browser.ui

import android.app.Application
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.duckduckgo.mobile.android.R as MobileR

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PulseAnimationTest {

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        override val lifecycle: Lifecycle get() = registry
    }

    private lateinit var appContext: Application
    private lateinit var testee: PulseAnimation

    private lateinit var root: FrameLayout
    private lateinit var fireButtonParent: FrameLayout
    private lateinit var privacyShieldParent: FrameLayout
    private lateinit var fireButton: View
    private lateinit var privacyShield: View

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        appContext.setTheme(MobileR.style.Theme_DuckDuckGo_Light)

        testee = PulseAnimation(TestLifecycleOwner())

        root = FrameLayout(appContext)
        fireButtonParent = FrameLayout(appContext).also { root.addView(it) }
        fireButton = View(appContext).also { fireButtonParent.addView(it, SIZE, SIZE) }
        privacyShieldParent = FrameLayout(appContext).also { root.addView(it) }
        privacyShield = View(appContext).also { privacyShieldParent.addView(it, SIZE, SIZE) }

        layoutRoot()
    }

    private fun layoutRoot() {
        root.measure(
            MeasureSpec.makeMeasureSpec(ROOT_SIZE, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(ROOT_SIZE, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, ROOT_SIZE, ROOT_SIZE)
    }

    /** Children the pulse added; the target itself is always the last child of its parent. */
    private fun FrameLayout.highlights(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it !== fireButton && it !== privacyShield }

    @Test
    fun whenPlayOnCalledThenHighlightAddedToTargetParent() {
        testee.playOn(fireButton)

        assertEquals(1, fireButtonParent.highlights().size)
    }

    @Test
    fun whenPlayOnCalledThenHighlightSizedFromTarget() {
        testee.playOn(fireButton)

        val highlight = fireButtonParent.highlights().single()
        assertEquals(SIZE, highlight.layoutParams.width)
        assertEquals(SIZE, highlight.layoutParams.height)
    }

    @Test
    fun whenPlayOnCalledTwiceForSameTargetThenHighlightNotDuplicated() {
        testee.playOn(fireButton)
        testee.playOn(fireButton)

        assertEquals(1, fireButtonParent.highlights().size)
    }

    @Test
    fun whenPlayOnCalledForDifferentTargetThenHighlightMovesToNewTarget() {
        testee.playOn(privacyShield)

        testee.playOn(fireButton)

        assertEquals(1, fireButtonParent.highlights().size)
        assertEquals(0, privacyShieldParent.highlights().size)
    }

    @Test
    fun whenTargetNotYetLaidOutThenHighlightAddedOnceItIs() {
        val lateParent = FrameLayout(appContext).also { root.addView(it) }
        val lateTarget = View(appContext).also { lateParent.addView(it, SIZE, SIZE) }

        testee.playOn(lateTarget)
        assertEquals(0, lateParent.childCount - 1)

        layoutRoot()

        assertEquals(1, lateParent.childCount - 1)
        assertTrue(lateParent.getChildAt(0).layoutParams.width > 0)
    }

    @Test
    fun whenStoppedThenHighlightRemoved() {
        testee.playOn(fireButton)

        testee.stop()

        assertEquals(0, fireButtonParent.highlights().size)
    }

    @Test
    fun whenStoppedThenPlayOnAddsHighlightAgain() {
        testee.playOn(fireButton)
        testee.stop()

        testee.playOn(fireButton)

        assertEquals(1, fireButtonParent.highlights().size)
    }

    companion object {
        private const val SIZE = 40
        private const val ROOT_SIZE = 200
    }
}
