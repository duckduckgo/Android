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
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary

data class DaxSubscriptionBrandDesignUpdateBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
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
) {
    override val activeIncludeId: Int = R.id.stackedCta

    override fun configureContentViews(view: View) {
        view.findViewById<ImageView>(R.id.brandDesignHeaderImage)?.isVisible = true

        val buttonTextRes = if (isFreeTrialCopy) {
            R.string.onboardingPrivacyProDaxDialogFreeTrialOkButton
        } else {
            R.string.onboardingPrivacyProDaxDialogOkButton
        }

        ctaView?.findViewById<TextView>(R.id.brandDesignHiddenTitle)?.gravity = Gravity.CENTER
        ctaView?.findViewById<TextView>(R.id.brandDesignTitle)?.gravity = Gravity.CENTER

        ctaView?.findViewById<TextView>(R.id.brandDesignDescription)?.gravity = Gravity.CENTER

        view.findViewById<DaxButtonPrimary>(R.id.brandDesignPrimaryCta)?.setText(buttonTextRes)
        view.findViewById<DaxButtonSecondary>(R.id.brandDesignSecondaryCta)?.apply {
            setText(R.string.noThanks)
            isVisible = true
        }
    }

    override fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<DaxButtonPrimary>(R.id.brandDesignPrimaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    override fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<DaxButtonSecondary>(R.id.brandDesignSecondaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }
}
