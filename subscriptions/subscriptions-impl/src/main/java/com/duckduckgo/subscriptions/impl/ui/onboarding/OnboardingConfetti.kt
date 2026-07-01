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

package com.duckduckgo.subscriptions.impl.ui.onboarding

import androidx.core.content.res.ResourcesCompat
import nl.dionsegijn.konfetti.KonfettiView
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Streams a short burst of brand-coloured confetti from the top centre of this view. Mirrors the
 * celebration used in App Tracking Protection's `DeviceShieldTrackerActivity`.
 */
fun KonfettiView.launchOnboardingConfetti() {
    val resources = context.resources
    val magenta = ResourcesCompat.getColor(resources, CommonR.color.magenta, null)
    val blue = ResourcesCompat.getColor(resources, CommonR.color.blue30, null)
    val purple = ResourcesCompat.getColor(resources, CommonR.color.purple, null)
    val green = ResourcesCompat.getColor(resources, CommonR.color.green, null)
    val yellow = ResourcesCompat.getColor(resources, CommonR.color.yellow, null)

    val displayWidth = resources.displayMetrics.widthPixels

    build()
        .addColors(magenta, blue, purple, green, yellow)
        .setDirection(0.0, 359.0)
        .setSpeed(4f, 9f)
        .setFadeOutEnabled(true)
        .setTimeToLive(1500L)
        .addShapes(Shape.Rectangle(1f))
        .addSizes(Size(8))
        .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
        .streamFor(60, 2000L)
}
