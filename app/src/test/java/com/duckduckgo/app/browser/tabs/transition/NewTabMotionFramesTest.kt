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

import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.BELOW_CENTER
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.RIGHT_OF_CENTER
import org.junit.Assert.assertEquals
import org.junit.Test

class NewTabMotionFramesTest {

    private val rightOfCenter = NewTabTransitionConfig.DEFAULT.motionFor(RIGHT_OF_CENTER)
    private val belowCenter = NewTabTransitionConfig.DEFAULT.motionFor(BELOW_CENTER)

    @Test
    fun incomingAtStart() {
        val frame = rightOfCenter.incomingFrame(0f)

        assertEquals(0.8f, frame.scale, DELTA)
        assertEquals(0.25f, frame.translationXFraction, DELTA)
        assertEquals(0f, frame.translationYFraction, DELTA)
        assertEquals(0f, frame.alpha, DELTA)
        assertEquals(1.25f, frame.cornerRadiusFactor, DELTA)
        assertEquals(1f, frame.shadowFraction, DELTA)
    }

    @Test
    fun incomingAtFadeEnd() {
        assertEquals(1f, rightOfCenter.incomingFrame(0.3f).alpha, DELTA)
    }

    @Test
    fun incomingAtEnd() {
        val frame = rightOfCenter.incomingFrame(1f)

        assertEquals(1f, frame.scale, DELTA)
        assertEquals(0f, frame.translationXFraction, DELTA)
        assertEquals(0f, frame.translationYFraction, DELTA)
        assertEquals(1f, frame.alpha, DELTA)
        assertEquals(1f, frame.cornerRadiusFactor, DELTA)
        assertEquals(0f, frame.shadowFraction, DELTA)
    }

    @Test
    fun outgoingAtStart() {
        val frame = rightOfCenter.outgoingFrame(0f, timeFraction = 0f)

        assertEquals(1f, frame.scale, DELTA)
        assertEquals(0f, frame.translationXFraction, DELTA)
        assertEquals(0f, frame.dimAlpha, DELTA)
    }

    @Test
    fun outgoingDimFollowsTimeNotEasedProgress() {
        assertEquals(0.125f, rightOfCenter.outgoingFrame(0.9f, timeFraction = 0.5f).dimAlpha, DELTA)
    }

    @Test
    fun outgoingAtEnd() {
        val frame = rightOfCenter.outgoingFrame(1f, timeFraction = 1f)

        assertEquals(0.95f, frame.scale, DELTA)
        assertEquals(-0.25f, frame.translationXFraction, DELTA)
        assertEquals(0.25f, frame.dimAlpha, DELTA)
    }

    @Test
    fun belowCenterMovesVerticallyOnly() {
        val incoming = belowCenter.incomingFrame(0f)

        assertEquals(0f, incoming.translationXFraction, DELTA)
        assertEquals(0.15f, incoming.translationYFraction, DELTA)
        assertEquals(0f, belowCenter.outgoingFrame(1f, timeFraction = 1f).translationXFraction, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
