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

data class FillTransform(
    val scale: Float,
    val translateX: Float,
    val translateY: Float,
)

/**
 * Height-driven fill transform anchored to the END (right) + BOTTOM edges: scales the drawable so its
 * height matches the view height, then right-aligns it. On a narrow view (phone) the scaled width exceeds
 * the view, so the left overflows off-screen and is clipped — the intended zoom-crop. On a wide view
 * (tablet landscape) the scaled width is narrower than the view, so the whole illustration shows
 * right-anchored at the chosen height. Either way the view height fully controls the rendered size.
 */
fun endBottomFillTransform(
    viewWidth: Int,
    viewHeight: Int,
    drawableWidth: Int,
    drawableHeight: Int,
): FillTransform {
    if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
        return FillTransform(scale = 1f, translateX = 0f, translateY = 0f)
    }
    val scale = viewHeight.toFloat() / drawableHeight
    return FillTransform(
        scale = scale,
        translateX = viewWidth - drawableWidth * scale,
        translateY = viewHeight - drawableHeight * scale,
    )
}

/**
 * Caps a requested fill height to a fraction of a reference height, keeping the band from dominating
 * large screens (tablets). Callers pass the device long edge (portrait height) as the reference so the cap
 * is orientation-independent and the band stays the same height in portrait and landscape. A fraction of 1f
 * or greater, or a non-positive reference height, applies no cap.
 */
fun cappedFillHeightPx(
    requestedPx: Int,
    referenceHeightPx: Int,
    maxHeightFraction: Float,
): Int {
    if (maxHeightFraction >= 1f || referenceHeightPx <= 0) return requestedPx
    val cap = (referenceHeightPx * maxHeightFraction).toInt()
    return minOf(requestedPx, cap)
}
