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
 * STUB: brand-design rebrand of [OnboardingDaxDialogCta.DaxNoTrackersCta]. A Stage 2 agent will
 * replace [activeIncludeId] and populate [configureContentViews] to render the
 * no-trackers-blocked in-context dialog.
 */
data class DaxNoTrackersBrandDesignUpdateContextualCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
    ctaId = CtaId.DAX_DIALOG_OTHER,
    description = R.string.daxNonSerpCtaText,
    buttonText = R.string.daxDialogGotIt,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    cancelPixel = null,
    closePixel = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_NO_TRACKERS_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
) {
    override val activeIncludeId: Int = 0 // STUB: Stage 2 replaces with R.id.<include>.

    override fun configureContentViews(view: View) {
        // STUB: Stage 2 agent implements.
    }
}
