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

package com.duckduckgo.app.browser.progressbar

import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class IndeterminateSweepRendererTest {

    private val renderer = IndeterminateSweepRenderer(
        config = ProgressBarConfig(),
        barColor = 0,
    )

    @Test
    fun `start activates renderer and stop deactivates it`() {
        assertFalse(renderer.isActive)

        renderer.start(now = 1000L, seedLeadingFraction = 0.5f)

        assertTrue(renderer.isActive)
        renderer.stop()
        assertFalse(renderer.isActive)
    }

    @Test
    fun `requestFinish reports finished only after the slow sweep exits`() {
        renderer.start(now = 1000L, seedLeadingFraction = 0.5f)

        renderer.requestFinish(now = 1500L)

        assertFalse(renderer.hasFinished(now = 3049L))
        assertTrue(renderer.hasFinished(now = 3050L))
    }

    @Test
    fun `requestFinish during overlap finishes the previous sequence`() {
        renderer.start(now = 1000L, seedLeadingFraction = 0.5f)

        renderer.requestFinish(now = 3025L)

        assertFalse(renderer.hasFinished(now = 3049L))
        assertTrue(renderer.hasFinished(now = 3050L))
    }

    @Test
    fun `second requestFinish does not move the finish time`() {
        renderer.start(now = 1000L, seedLeadingFraction = 0.5f)
        renderer.requestFinish(now = 1500L)

        renderer.requestFinish(now = 4000L)

        assertTrue(renderer.hasFinished(now = 4000L))
    }

    @Test
    fun `requestFinish suppresses the next fast sweep during overlap`() {
        val canvas: Canvas = mock()
        renderer.start(now = 1000L, seedLeadingFraction = 0.5f)

        renderer.draw(canvas, trackWidth = 100f, top = 0f, bottom = 2f, now = 3025L)
        verify(canvas, times(2)).drawRoundRect(any(), any(), any(), any(), any(), any(), any())
        clearInvocations(canvas)

        renderer.requestFinish(now = 3025L)
        renderer.draw(canvas, trackWidth = 100f, top = 0f, bottom = 2f, now = 3025L)

        verify(canvas, times(1)).drawRoundRect(any(), any(), any(), any(), any(), any(), any())
    }
}
