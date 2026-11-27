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

/** The new tab at one point of the transition; translations are fractions of its own size. */
data class IncomingFrame(
    val scale: Float,
    val translationXFraction: Float,
    val translationYFraction: Float,
    val alpha: Float,
    val cornerRadiusFactor: Float,
    val shadowFraction: Float,
)

/** The page being covered at one point of the transition; translation is a fraction of its own width. */
data class OutgoingFrame(
    val scale: Float,
    val translationXFraction: Float,
    val dimAlpha: Float,
)

// The new tab's properties are driven by the same eased progress, so the keyframes are fractions of it.
fun NewTabMotion.incomingFrame(progress: Float): IncomingFrame = IncomingFrame(
    scale = lerp(startScale, 1f, progress),
    translationXFraction = lerp(offsetXFraction, 0f, progress),
    translationYFraction = lerp(offsetYFraction, 0f, progress),
    alpha = (progress / FADE_IN_END).coerceIn(0f, 1f),
    // Starts at the screen's corner radius once scaled down, and ends at it.
    cornerRadiusFactor = lerp(1f / startScale, 1f, progress),
    shadowFraction = 1f - progress,
)

fun NewTabMotion.outgoingFrame(
    progress: Float,
    timeFraction: Float,
): OutgoingFrame = OutgoingFrame(
    scale = lerp(1f, backgroundScale, progress),
    translationXFraction = -slideLeftFraction * progress,
    dimAlpha = dimAlpha * timeFraction.coerceIn(0f, 1f),
)

private fun lerp(
    from: Float,
    to: Float,
    progress: Float,
): Float = from + (to - from) * progress

private const val FADE_IN_END = 0.3f
