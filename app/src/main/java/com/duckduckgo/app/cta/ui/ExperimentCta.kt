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
import com.duckduckgo.app.onboarding.ui.page.experiment.OnboardingExperimentPixel.PixelName
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.mobile.android.R as commonR

sealed class ExperimentDaxBubbleOptionsCta(
    override val ctaId: CtaId,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    open val options: List<DaxDialogIntroOption>,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ViewCta, DaxCta {

    private var ctaView: View? = null

    override fun showCta(view: View) {
        ctaView = view
        val daxTitle = view.context.getString(title)
        val daxText = view.context.getString(description)
        view.show()
        view.alpha = 1f
        view.findViewById<DaxTextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(R.id.experimentDialogTitle).text = daxTitle.html(view.context)
        options[0].setOptionView(view.findViewById(R.id.daxDialogOption1))
        options[1].setOptionView(view.findViewById(R.id.daxDialogOption2))
        options[2].setOptionView(view.findViewById(R.id.daxDialogOption3))
        options[3].setOptionView(view.findViewById(R.id.daxDialogOption4))
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true)
    }

    fun setOnOptionClicked(onOptionClicked: (DaxDialogIntroOption) -> Unit) {
        ctaView?.let { view ->
            view.findViewById<DaxButton>(R.id.daxDialogOption1).setOnClickListener { onOptionClicked.invoke(options[0]) }
            view.findViewById<DaxButton>(R.id.daxDialogOption2).setOnClickListener { onOptionClicked.invoke(options[1]) }
            view.findViewById<DaxButton>(R.id.daxDialogOption3).setOnClickListener { onOptionClicked.invoke(options[2]) }
            view.findViewById<DaxButton>(R.id.daxDialogOption4).setOnClickListener { onOptionClicked.invoke(options[3]) }
        }
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
        DaxDialogIntroOption.getSearchOptions(),
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
        DaxDialogIntroOption.getSitesOptions(),
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore,
        appInstallStore,
    )
}

data class DaxDialogIntroOption(
    @StringRes val textRes: Int,
    @DrawableRes val iconRes: Int,
    val link: String,
    val pixel: PixelName,
) {
    fun setOptionView(buttonView: DaxButton) {
        buttonView.apply {
            text = this.context.getString(textRes)
            icon = ContextCompat.getDrawable(this.context, iconRes)
        }
    }

    companion object {
        fun getSearchOptions(): List<DaxDialogIntroOption> =
            listOf(
                DaxDialogIntroOption(
                    R.string.onboardingSearchDaxDialogOption1,
                    commonR.drawable.ic_find_search_16,
                    "how to say “duck” in spanish",
                    PixelName.ONBOARDING_SEARCH_SAY_DUCK,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSearchDaxDialogOption2,
                    commonR.drawable.ic_find_search_16,
                    "mighty ducks cast",
                    PixelName.ONBOARDING_SEARCH_MIGHTY_DUCK,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSearchDaxDialogOption3,
                    commonR.drawable.ic_find_search_16,
                    "local weather",
                    PixelName.ONBOARDING_SEARCH_WEATHER,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSearchDaxDialogOption4,
                    commonR.drawable.ic_wand_16,
                    "chocolate chip cookie recipes",
                    PixelName.ONBOARDING_SEARCH_SURPRISE_ME,
                ),
            )

        fun getSitesOptions(): List<DaxDialogIntroOption> =
            listOf(
                DaxDialogIntroOption(
                    R.string.onboardingSitesDaxDialogOption1,
                    commonR.drawable.ic_globe_gray_16dp,
                    "espn.com",
                    PixelName.ONBOARDING_VISIT_SITE_ESPN,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSitesDaxDialogOption2,
                    commonR.drawable.ic_globe_gray_16dp,
                    "yahoo.com",
                    PixelName.ONBOARDING_VISIT_SITE_YAHOO,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSitesDaxDialogOption3,
                    commonR.drawable.ic_globe_gray_16dp,
                    "ebay.com",
                    PixelName.ONBOARDING_VISIT_SITE_EBAY,
                ),
                DaxDialogIntroOption(
                    R.string.onboardingSitesDaxDialogOption4,
                    commonR.drawable.ic_wand_16,
                    "britannica.com/animal/duck",
                    PixelName.ONBOARDING_VISIT_SITE_SURPRISE_ME,
                ),
            )
    }
}
