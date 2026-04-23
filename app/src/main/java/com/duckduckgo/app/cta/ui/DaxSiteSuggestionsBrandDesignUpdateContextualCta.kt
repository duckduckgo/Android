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
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.view.DaxTypeAnimationTextView
import com.duckduckgo.app.onboarding.ui.view.OnboardingSelectionButton
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extensions.html

data class DaxSiteSuggestionsBrandDesignUpdateContextualCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    val onSiteSuggestionOptionClicked: (index: Int) -> Unit,
    override val isLightTheme: Boolean,
) : OnboardingDaxDialogCta.BrandDesignContextualDaxDialogCta(
    ctaId = CtaId.DAX_INTRO_VISIT_SITE,
    description = R.string.onboardingSitesDaxDialogDescription,
    buttonText = null,
    shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
    okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
    cancelPixel = null,
    closePixel = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
) {
    override val activeIncludeId: Int = R.id.contextualBrandDesignOptionsContent

    override fun configureContentViews(view: View) {
        val context = view.context
        view.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
            .text = context.getString(R.string.onboardingSitesSuggestionsDaxDialogTitle).html(context)
        view.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription)
            .text = context.getString(R.string.onboardingSitesDaxDialogDescription).html(context)

        val options = onboardingStore.getSitesOptions()
        val optionsViews = listOf(
            view.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption1),
            view.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption2),
            view.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption3),
            view.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption4),
        )
        optionsViews.forEachIndexed { index, buttonView ->
            if (index < options.size) {
                options[index].setOptionView(buttonView)
            }
        }
    }

    override fun setOnOptionClicked(onOptionClicked: ((DaxDialogIntroOption) -> Unit)?) {
        val container = ctaView ?: return
        val options = onboardingStore.getSitesOptions()
        val optionsViews = listOf(
            container.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption1),
            container.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption2),
            container.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption3),
            container.findViewById<OnboardingSelectionButton>(R.id.contextualBrandDesignSiteOption4),
        )
        optionsViews.forEachIndexed { index, buttonView ->
            if (index < options.size) {
                val option = options[index]
                buttonView.setOnClickListener { onOptionClicked?.invoke(option) }
            }
        }
    }
}
