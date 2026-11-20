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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.Guideline
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R

/** Exterior View references shared by the native and Compose dialog hosts. */
class OnboardingStage(val root: View) {
    val dialog: ViewGroup = root.findViewById(R.id.daxDialogCta)
    val logoAnimation: LottieAnimationView = root.findViewById(R.id.logoAnimation)
    val welcomeTitle: TextView = root.findViewById(R.id.welcomeTitle)
    val textGuideline: Guideline = root.findViewById(R.id.textGuideline)
    val duckAiIntroAnimation: LottieAnimationView = root.findViewById(R.id.duckAiIntroAnimation)
    val backgroundPrimary: LottieAnimationView = root.findViewById(R.id.backgroundPrimary)
    val backgroundSecondary: ImageView = root.findViewById(R.id.backgroundSecondary)
    val welcomeScreenWalkingDax: LottieAnimationView = root.findViewById(R.id.welcomeScreenWalkingDax)
    val bottomWingAnimation: LottieAnimationView = root.findViewById(R.id.bottomWingAnimation)
    val leftWingAnimation: LottieAnimationView = root.findViewById(R.id.leftWingAnimation)
    val rightWingAnimation: LottieAnimationView = root.findViewById(R.id.rightWingAnimation)
    val bobbingDaxAnimation: LottieAnimationView = root.findViewById(R.id.bobbingDaxAnimation)
    val undecoratedBand: View = root.findViewById(R.id.undecoratedBand)
}
