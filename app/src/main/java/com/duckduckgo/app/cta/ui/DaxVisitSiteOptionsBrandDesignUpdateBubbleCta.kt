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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.pixels.Pixel

data class DaxVisitSiteOptionsBrandDesignUpdateBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
) : OptionsBubbleCta(
    ctaId = CtaId.DAX_INTRO_VISIT_SITE,
    title = R.string.onboardingSitesDaxDialogTitle,
    description = R.string.onboardingSitesDaxDialogDescription,
    options = onboardingStore.getSitesOptions(),
    backgroundRes = R.drawable.bg_onboarding_site_options,
    ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
)
