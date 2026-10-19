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

package com.duckduckgo.app.browser.tabs

/** Where a new tab starts growing from, relative to the centre of the screen. */
enum class NewTabOrigin {
    BELOW_CENTER,
    RIGHT_OF_CENTER,
}

data class OriginProfile(
    val startSizePercent: Int,
    val offsetPercent: Int,
    val scaleBackPercent: Int,
    val dimPercent: Int,
    val slideLeftPercent: Int,
    val durationMs: Int,
)

data class NewTabTransitionConfig(
    val browserOrigin: NewTabOrigin,
    val tabManagerOrigin: NewTabOrigin,
    val belowCenter: OriginProfile,
    val rightOfCenter: OriginProfile,
    val animateClose: Boolean,
    val animateTabManagerOut: Boolean,
    val slowMotion: Boolean,
) {
    fun profile(origin: NewTabOrigin): OriginProfile = when (origin) {
        NewTabOrigin.BELOW_CENTER -> belowCenter
        NewTabOrigin.RIGHT_OF_CENTER -> rightOfCenter
    }

    fun motionFor(origin: NewTabOrigin): NewTabMotion {
        val profile = profile(origin)
        val offset = profile.offsetPercent.toFraction()
        return NewTabMotion(
            startScale = profile.startSizePercent.toFraction(),
            offsetXFraction = if (origin == NewTabOrigin.RIGHT_OF_CENTER) offset else 0f,
            offsetYFraction = if (origin == NewTabOrigin.BELOW_CENTER) offset else 0f,
            backgroundScale = profile.scaleBackPercent.toFraction(),
            dimAlpha = profile.dimPercent.toFraction(),
            // Sliding left only fits a new tab coming in from the right.
            slideLeftFraction = if (origin == NewTabOrigin.RIGHT_OF_CENTER) profile.slideLeftPercent.toFraction() else 0f,
            durationMs = profile.durationMs.toLong() * if (slowMotion) SLOW_MOTION_FACTOR else 1,
        )
    }

    private fun Int.toFraction(): Float = this / 100f

    companion object {
        val DEFAULT = NewTabTransitionConfig(
            browserOrigin = NewTabOrigin.RIGHT_OF_CENTER,
            tabManagerOrigin = NewTabOrigin.BELOW_CENTER,
            belowCenter = OriginProfile(
                startSizePercent = 80,
                offsetPercent = 15,
                scaleBackPercent = 95,
                dimPercent = 25,
                slideLeftPercent = 0,
                durationMs = 360,
            ),
            rightOfCenter = OriginProfile(
                startSizePercent = 80,
                offsetPercent = 25,
                scaleBackPercent = 95,
                dimPercent = 25,
                slideLeftPercent = 25,
                durationMs = 360,
            ),
            animateClose = true,
            animateTabManagerOut = false,
            slowMotion = false,
        )

        val START_SIZE_RANGE = 40..100
        val BELOW_CENTER_OFFSET_RANGE = -30..50
        val RIGHT_OF_CENTER_OFFSET_RANGE = 0..30
        val SCALE_BACK_RANGE = 75..100
        val DIM_RANGE = 0..100
        val DURATION_RANGE = 150..900
        const val DURATION_STEP = 10
        val SLIDE_LEFT_RANGE = 0..50

        private const val SLOW_MOTION_FACTOR = 4
    }
}

/** A [NewTabTransitionConfig] resolved for one origin: scales and offsets as fractions of the screen. */
data class NewTabMotion(
    val startScale: Float,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val backgroundScale: Float,
    val dimAlpha: Float,
    val slideLeftFraction: Float,
    val durationMs: Long,
)
