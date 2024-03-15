/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extensions.html

sealed class ExperimentDaxBubbleOptionsCta(
    override val ctaId: CtaId,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @DrawableRes open val optionsIcon: Int,
    open val options: List<Int>,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ViewCta, DaxCta {

    override fun showCta(view: View) {
        val daxTitle = view.context.getString(title)
        val daxText = view.context.getString(description)
        val daxOptionIcon = ContextCompat.getDrawable(view.context, optionsIcon)
        val daxOptionsText = options.map { view.context.getString(it) }
        view.show()
        view.alpha = 1f
        view.findViewById<DaxTextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(R.id.experimentDialogTitle).text = daxTitle.html(view.context)
        view.findViewById<DaxButton>(R.id.daxDialogOption1).apply {
            text = daxOptionsText[0]
            icon = daxOptionIcon
        }
        view.findViewById<DaxButton>(R.id.daxDialogOption2).apply {
            text = daxOptionsText[1]
            icon = daxOptionIcon
        }
        view.findViewById<DaxButton>(R.id.daxDialogOption3).apply {
            text = daxOptionsText[2]
            icon = daxOptionIcon
        }
        view.findViewById<DaxButton>(R.id.daxDialogOption4).text = daxOptionsText[3]
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true)
    }

    fun setOnOptionClicked(view: View, onOptionClicked: (String) -> Unit) {
        val daxOptionsText = options.map { view.context.getString(it) }
        view.findViewById<DaxButton>(R.id.daxDialogOption1).setOnClickListener { onOptionClicked.invoke(daxOptionsText[0]) }
        view.findViewById<DaxButton>(R.id.daxDialogOption2).setOnClickListener { onOptionClicked.invoke(daxOptionsText[1]) }
        view.findViewById<DaxButton>(R.id.daxDialogOption3).setOnClickListener { onOptionClicked.invoke(daxOptionsText[2]) }
        view.findViewById<DaxButton>(R.id.daxDialogOption4).setOnClickListener { onOptionClicked.invoke(daxOptionsText[3]) }
    }

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    data class ExperimentDaxIntroSearchOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentDaxBubbleOptionsCta(
        CtaId.DAX_INTRO,
        R.string.onboardingSearchDaxDialogTitle,
        R.string.onboardingSearchDaxDialogDescription,
        com.duckduckgo.mobile.android.R.drawable.ic_find_search_16,
        listOf(
            R.string.onboardingSearchDaxDialogOption1,
            R.string.onboardingSearchDaxDialogOption2,
            R.string.onboardingSearchDaxDialogOption3,
            R.string.onboardingSearchDaxDialogOption4,
        ),
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore,
        appInstallStore,
    )

    data class ExperimentDaxIntroVisitSiteOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentDaxBubbleOptionsCta(
        CtaId.DAX_INTRO_VISIT_SITE,
        R.string.onboardingSitesDaxDialogTitle,
        R.string.onboardingSitesDaxDialogDescription,
        com.duckduckgo.mobile.android.R.drawable.ic_globe_gray_16dp,
        listOf(
            R.string.onboardingSitesDaxDialogOption1,
            R.string.onboardingSitesDaxDialogOption2,
            R.string.onboardingSitesDaxDialogOption3,
            R.string.onboardingSitesDaxDialogOption4,
        ),
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore,
        appInstallStore,
    )
}
