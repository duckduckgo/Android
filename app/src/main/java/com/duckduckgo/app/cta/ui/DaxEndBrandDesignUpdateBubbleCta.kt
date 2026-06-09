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
import com.duckduckgo.common.utils.device.DeviceInfo
import com.google.android.material.button.MaterialButton
import com.duckduckgo.mobile.android.R as CommonR

data class DaxEndBrandDesignUpdateBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
    override val deviceInfo: DeviceInfo,
    override val onboardingImprovementsEnabled: Boolean,
    val isOmnibarBottom: Boolean,
) : DaxBubbleCta.BrandDesignUpdateBubbleCta(
    ctaId = CtaId.DAX_END,
    title = R.string.onboardingEndDaxDialogTitle,
    description = R.string.onboardingEndDaxDialogDescription,
    backgroundRes = CommonR.drawable.bg_onboarding_end,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
    deviceInfo = deviceInfo,
    onboardingImprovementsEnabled = onboardingImprovementsEnabled,
),
    DaxBubbleCta.ShowsWavingDax {
    override val activeIncludeId: Int = R.id.primaryCta
    override val showArrow: Boolean = true
    override val wavingDaxSpec = WavingDaxSpec(
        rotationDegrees = 0f,
        // Bottom address bar (with native input enabled) doesn't stretch to the edge of the screen,
        // so we need to nudge Dax to the right to ensure both his legs are behind the address bar.
        translationXDp = if (isOmnibarBottom) -30f else -40f,
        translationYDp = -150f,
        minHeightDp = 178f,
        maxHeightDp = 178f,
        anchorToCardOnTablet = true,
    )

    override fun configureContentViews(view: View) {
        view.findViewById<MaterialButton>(R.id.primaryCta)?.setText(R.string.onboardingEndDaxDialogButton)
    }
}
