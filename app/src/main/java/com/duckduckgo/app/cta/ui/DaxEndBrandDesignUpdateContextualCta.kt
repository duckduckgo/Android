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

import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel

/**
 * STUB: brand-design rebrand of [OnboardingDaxDialogCta.DaxEndCta] (the in-context end-of-journey
 * variant — distinct from [DaxEndBrandDesignUpdateBubbleCta], which is the NTP bubble). A Stage 2
 * agent will replace [activeIncludeId] and populate [configureContentViews].
 */
data class DaxEndBrandDesignUpdateContextualCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
    ctaId = CtaId.DAX_END,
    description = R.string.onboardingEndDaxDialogDescription,
    buttonText = R.string.onboardingEndDaxDialogButton,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    cancelPixel = null,
    closePixel = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_ONBOARDING_END_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
) {
    override val markAsReadOnShow: Boolean = true

    override val activeIncludeId: Int = 0 // TODO: replace in stage 2.

    override fun configureContentViews(view: View) {
        // TODO: implement in stage 2.
    }
}
