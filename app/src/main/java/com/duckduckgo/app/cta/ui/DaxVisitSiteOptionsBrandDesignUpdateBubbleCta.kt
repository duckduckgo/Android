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
import com.duckduckgo.app.onboarding.ui.view.OnboardingSelectionButton
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show

data class DaxVisitSiteOptionsBrandDesignUpdateBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
) : DaxBubbleCta.BrandDesignUpdateBubbleCta(
    ctaId = CtaId.DAX_INTRO_VISIT_SITE,
    title = R.string.onboardingSitesDaxDialogTitle,
    description = R.string.onboardingSitesDaxDialogDescription,
    options = onboardingStore.getSitesOptions(),
    backgroundRes = 0, // TODO: Update background for brand design update
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
) {
    override val activeIncludeId: Int = R.id.optionsContent

    override fun configureContentViews(view: View) {
        val optionViews: List<OnboardingSelectionButton> = listOf(
            view.findViewById(R.id.brandDesignOption1),
            view.findViewById(R.id.brandDesignOption2),
            view.findViewById(R.id.brandDesignOption3),
            view.findViewById(R.id.brandDesignOption4),
        )
        options?.let {
            optionViews.forEachIndexed { index, buttonView ->
                buttonView.show()
                if (it.size > index) {
                    it[index].setOptionView(buttonView)
                } else {
                    buttonView.gone()
                }
            }
        }
    }

    override fun setOnOptionClicked(
        onboardingExperimentEnabled: Boolean,
        configuration: DaxBubbleCta?,
        onOptionClicked: (DaxDialogIntroOption, index: Int?) -> Unit,
    ) {
        options?.forEachIndexed { index, option ->
            val optionViewId = when (index) {
                0 -> R.id.brandDesignOption1
                1 -> R.id.brandDesignOption2
                2 -> R.id.brandDesignOption3
                else -> R.id.brandDesignOption4
            }
            ctaView?.findViewById<OnboardingSelectionButton>(optionViewId)?.setOnClickListener {
                onOptionClicked.invoke(option, index)
            }
        }
    }
}
