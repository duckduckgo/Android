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

package com.duckduckgo.app.browser.tabs.transition

import android.animation.TimeAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.RIGHT_OF_CENTER
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class NewTabTransitionPlayerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = FrameLayout(context)
    private val cover = ImageView(context).apply { visibility = View.GONE }
    private val containerBackground = ColorDrawable()
    private val container = FrameLayout(context).apply { background = containerBackground }
    private val state = RealNewTabTransitionState()
    private val motion = NewTabTransitionConfig.DEFAULT.motionFor(RIGHT_OF_CENTER)

    private val testee = NewTabTransitionPlayer(cover, state)

    @Before
    fun setUp() {
        root.addView(cover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.layoutAt(WIDTH, HEIGHT)
    }

    @After
    fun tearDown() {
        testee.cancel()
        setAnimatorDurationScale(1f)
    }

    @Test
    fun playAppliesStartPoseAndMarksRunning() {
        testee.play(container, picture(), motion)

        assertEquals(0.8f, container.scaleX, DELTA)
        assertEquals(0f, container.alpha, DELTA)
        assertTrue(container.clipToOutline)
        assertEquals(View.VISIBLE, cover.visibility)
        assertTrue(state.isRunning.value)
    }

    @Test
    fun playRunsToEndAndRestores() {
        var ended = 0
        testee.play(container, picture(), motion) { ended++ }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))

        assertRestored()
        assertEquals(1, ended)
    }

    @Test
    fun playWithAnimationsOffEndsAtOnce() {
        var ended = 0
        setAnimatorDurationScale(0f)
        try {
            testee.play(container, picture(), motion) { ended++ }
        } finally {
            setAnimatorDurationScale(1f)
        }

        assertRestored()
        assertEquals(1, ended)
        assertFalse(state.isRunning.value)
    }

    @Test
    fun longFrameAdvancesTheClockByOneFrameOnly() {
        testee.play(container, picture(), motion)

        tick(animator(), deltaMs = 140)

        assertEquals(motion.incomingFrame(EASING.getInterpolation(20f / motion.durationMs)).scale, container.scaleX, DELTA)
        assertTrue(state.isRunning.value)
    }

    @Test
    fun animatorDurationScaleSlowsTheClock() {
        setAnimatorDurationScale(2f)
        testee.play(container, picture(), motion)

        tick(animator(), deltaMs = 16)

        assertEquals(motion.incomingFrame(EASING.getInterpolation(16f / (motion.durationMs * 2))).scale, container.scaleX, DELTA)
    }

    @Test
    fun endCallbackCanStartTheNextTransition() {
        var firstEnded = 0
        var secondEnded = 0
        testee.play(container, picture(), motion) {
            firstEnded++
            testee.play(container, picture(), motion) { secondEnded++ }
        }

        val first = animator()
        repeat(18) { tick(first, deltaMs = 20) }

        assertEquals(1, firstEnded)
        assertEquals(0, secondEnded)
        assertTrue(state.isRunning.value)
        testee.cancel()
        assertEquals(1, secondEnded)
        assertRestored()
    }

    @Test
    fun animationsOffLeaveNoClockRunning() {
        setAnimatorDurationScale(0f)

        testee.play(container, picture(), motion)

        assertFalse(animatorOrNull()?.isStarted == true)
    }

    @Test
    fun animationsOffEndCallbackKeepsTheNextTransitionsClock() {
        setAnimatorDurationScale(0f)
        var next: TimeAnimator? = null
        var nextEnded = 0

        testee.play(container, picture(), motion) {
            setAnimatorDurationScale(1f)
            testee.play(container, picture(), motion) { nextEnded++ }
            next = animator()
        }

        assertSame(next, animatorOrNull())
        assertEquals(0, nextEnded)
        assertTrue(state.isRunning.value)
    }

    @Test
    fun cancelRestoresContainerAndHidesCover() {
        var ended = 0
        testee.play(container, picture(), motion) { ended++ }

        testee.cancel()

        assertRestored()
        assertEquals(1, ended)
    }

    @Test
    fun playWhileRunningCleansUpFirst() {
        var firstEnded = 0
        testee.play(container, picture(), motion) { firstEnded++ }

        testee.play(container, picture(), motion)

        assertEquals(1, firstEnded)
        assertEquals(0.8f, container.scaleX, DELTA)
        assertEquals(View.VISIBLE, cover.visibility)
        assertTrue(state.isRunning.value)
    }

    @Test
    fun pictureSizeMismatchDoesNotAnimate() {
        var ended = 0

        testee.play(container, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), motion) { ended++ }

        assertRestored()
        assertEquals(1, ended)
    }

    @Test
    fun coverHidesContainerAndMarksRunning() {
        testee.cover(container, picture()) {}

        assertEquals(0f, container.alpha, DELTA)
        assertEquals(View.VISIBLE, cover.visibility)
        assertTrue(state.isRunning.value)
    }

    @Test
    fun coverTimeoutRestoresContainer() {
        var timedOut = false
        testee.cover(container, picture()) { timedOut = true }

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))

        assertTrue(timedOut)
        assertRestored()
    }

    @Test
    fun cancelEndsCover() {
        var ended = 0
        testee.cover(container, picture()) { ended++ }

        testee.cancel()

        assertRestored()
        assertEquals(1, ended)
    }

    @Test
    fun replacingPlayKeepsKeyboardHeld() {
        testee.play(container, picture(), motion)
        val released = awaitIdleInBackground()

        testee.play(container, picture(), motion)
        assertFalse(released())

        testee.cancel()
        assertTrue(released())
    }

    @Test
    fun playAfterCoverKeepsKeyboardHeld() {
        testee.cover(container, picture()) {}
        val released = awaitIdleInBackground()

        testee.play(container, picture(), motion)
        assertFalse(released())

        testee.cancel()
        assertTrue(released())
    }

    @Test
    fun pictureSizeMismatchOnReplacementReleasesKeyboard() {
        testee.play(container, picture(), motion)
        val released = awaitIdleInBackground()

        testee.play(container, Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), motion)

        assertTrue(released())
    }

    @Test
    fun containerResizeDuringPlayCancels() {
        var ended = 0
        testee.play(container, picture(), motion) { ended++ }

        container.layout(0, 0, HEIGHT, WIDTH)

        assertRestored()
        assertEquals(1, ended)
    }

    @Test
    fun containerRelayoutAtSameSizeKeepsPlaying() {
        testee.play(container, picture(), motion)

        container.layout(0, 0, WIDTH, HEIGHT)

        assertTrue(state.isRunning.value)
        assertEquals(View.VISIBLE, cover.visibility)
    }

    @Test
    fun coverPictureIsPlacedInWindowCoordinates() {
        // Window-local origin differs from the screen origin, as in split screen or a floating window.
        val offsetParent = object : FrameLayout(context) {
            override fun getLocationInWindow(outLocation: IntArray) {
                outLocation[0] = 40
                outLocation[1] = 60
            }

            override fun getLocationOnScreen(outLocation: IntArray) {
                outLocation[0] = 140
                outLocation[1] = 260
            }
        }
        offsetParent.setPadding(10, 20, 0, 0)
        val offsetCover = ImageView(context).apply { visibility = View.GONE }
        val offsetContainer = FrameLayout(context)
        offsetParent.addView(offsetCover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        offsetParent.addView(offsetContainer, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        offsetParent.layoutAt(WIDTH, HEIGHT)

        NewTabTransitionPlayer(offsetCover, state).play(offsetContainer, picture(), motion)
        offsetParent.layoutAt(WIDTH, HEIGHT)

        val values = FloatArray(9).also { offsetCover.imageMatrix.getValues(it) }
        assertEquals(-50f, values[Matrix.MTRANS_X], DELTA)
        assertEquals(-80f, values[Matrix.MTRANS_Y], DELTA)
    }

    private fun awaitIdleInBackground(): () -> Boolean {
        var released = false
        coroutineRule.testScope.launch {
            state.isRunning.first { !it }
            released = true
        }
        return { released }
    }

    private fun View.layoutAt(
        width: Int,
        height: Int,
    ) {
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, width, height)
    }

    private fun assertRestored() {
        assertEquals(1f, container.scaleX, DELTA)
        assertEquals(1f, container.scaleY, DELTA)
        assertEquals(0f, container.translationX, DELTA)
        assertEquals(0f, container.translationY, DELTA)
        assertEquals(0f, container.translationZ, DELTA)
        assertEquals(1f, container.alpha, DELTA)
        assertFalse(container.clipToOutline)
        assertSame(containerBackground, container.background)
        assertEquals(View.GONE, cover.visibility)
        assertNull(cover.drawable)
        assertFalse(state.isRunning.value)
    }

    // "Remove animations": the animator's own scale (read from API 33) and the system setting it mirrors (read below that).
    private fun setAnimatorDurationScale(scale: Float) {
        ValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun animatorOrNull(): TimeAnimator? = NewTabTransitionPlayer::class.java.getDeclaredField("animator").run {
        isAccessible = true
        get(testee) as TimeAnimator?
    }

    private fun animator(): TimeAnimator = requireNotNull(animatorOrNull())

    // Robolectric runs frames at a fixed step, so a long frame is delivered to the clock's listener directly.
    private fun tick(
        animator: TimeAnimator,
        deltaMs: Long,
    ) {
        val listener = TimeAnimator::class.java.getDeclaredField("mListener").run {
            isAccessible = true
            get(animator) as TimeAnimator.TimeListener
        }
        listener.onTimeUpdate(animator, deltaMs, deltaMs)
    }

    private fun picture(): Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)

    private companion object {
        val EASING = PathInterpolator(0.2f, 0f, 0f, 1f)
        const val WIDTH = 100
        const val HEIGHT = 200
        const val DELTA = 0.0001f
    }
}
