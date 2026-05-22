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

package com.duckduckgo.app.cta.ui

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.device.DeviceInfo
import com.google.android.material.button.MaterialButton

data class DaxSubscriptionBrandDesignUpdateBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
    override val deviceInfo: DeviceInfo,
    val isFreeTrialCopy: Boolean,
) : DaxBubbleCta.BrandDesignUpdateBubbleCta(
    ctaId = CtaId.DAX_INTRO_PRIVACY_PRO,
    title = R.string.onboardingPrivacyProDaxDialogTitle,
    description = R.string.onboardingPrivacyProDaxDialogDescription,
    backgroundRes = R.drawable.bg_onboarding_subscription,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_SUBSCRIPTION,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
    deviceInfo = deviceInfo,
),
    DaxBubbleCta.ShowsWavingDax {
    override val activeIncludeId: Int = R.id.primaryCta
    override val showArrow: Boolean = true
    override val restartWavingDax: Boolean = true

    override fun configureContentViews(view: View) {
        view.findViewById<ImageView>(R.id.brandDesignHeaderImage)?.isVisible = true

        val buttonTextRes = if (isFreeTrialCopy) {
            R.string.onboardingPrivacyProDaxDialogFreeTrialOkButton
        } else {
            R.string.onboardingPrivacyProDaxDialogOkButton
        }

        view.findViewById<TextView>(R.id.brandDesignHiddenTitle)?.gravity = Gravity.CENTER
        view.findViewById<TextView>(R.id.brandDesignTitle)?.gravity = Gravity.CENTER

        view.findViewById<TextView>(R.id.brandDesignDescription)?.gravity = Gravity.CENTER

        view.findViewById<MaterialButton>(R.id.primaryCta)?.setText(buttonTextRes)
    }

    override fun configureWavingDax(dax: LottieAnimationView, deviceInfo: DeviceInfo) {
        val density = dax.resources.displayMetrics.density
        dax.rotation = SUBSCRIPTION_DAX_ROTATION_DEGREES
        dax.translationX = SUBSCRIPTION_DAX_TRANSLATION_X_DP * density
        dax.translationY = SUBSCRIPTION_DAX_TRANSLATION_Y_DP * density
        (dax.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
            lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            lp.height = (SUBSCRIPTION_DAX_HEIGHT_DP * density).toInt()
            dax.layoutParams = lp
        }
    }

    private companion object {
        private const val SUBSCRIPTION_DAX_ROTATION_DEGREES = 22.62f
        // Baseline 178dp (layout default) scaled 1.5x; translations scaled in proportion to keep the
        // same relative offset from the bubble's bottom-left.
        private const val SUBSCRIPTION_DAX_HEIGHT_DP = 267f
        private const val SUBSCRIPTION_DAX_TRANSLATION_X_DP = -114f
        private const val SUBSCRIPTION_DAX_TRANSLATION_Y_DP = -288f
    }
}
