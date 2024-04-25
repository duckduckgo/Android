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

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeExperimentViewDaxDialogBinding
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.ExperimentDaxBubbleOptionsCta.DaxDialogIntroOption
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.experiment.OnboardingExperimentPixel.PixelName
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.mobile.android.R as commonR

sealed class ExperimentDaxBubbleOptionsCta(
    override val ctaId: CtaId,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    open val options: List<DaxDialogIntroOption>?,
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

        if (options.isNullOrEmpty()) {
            view.findViewById<DaxButton>(R.id.daxDialogOption1).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption2).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption3).gone()
            view.findViewById<DaxButton>(R.id.daxDialogOption4).gone()
        } else {
            options?.let {
                val optionsViews = listOf<DaxButton>(
                    view.findViewById(R.id.daxDialogOption1),
                    view.findViewById(R.id.daxDialogOption2),
                    view.findViewById(R.id.daxDialogOption3),
                    view.findViewById(R.id.daxDialogOption4),
                )
                optionsViews.forEachIndexed { index, buttonView ->
                    it[index].setOptionView(buttonView)
                    ViewCompat.animate(buttonView).alpha(1f).setDuration(500L).startDelay = 2800L
                }
            }
        }
        view.show()
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).text = ""
        view.findViewById<DaxTextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(R.id.experimentDialogTitle).apply {
            alpha = 0f
            text = daxTitle.html(view.context)
        }
        ViewCompat.animate(view).alpha(1f).setDuration(500).setStartDelay(600)
            .withEndAction {
                ViewCompat.animate(view.findViewById<DaxTextView>(R.id.experimentDialogTitle)).alpha(1f).setDuration(500)
                    .withEndAction {
                        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true)
                    }
            }
    }

    fun setOnOptionClicked(onOptionClicked: (DaxDialogIntroOption) -> Unit) {
        options?.let { options ->
            ctaView?.findViewById<DaxButton>(R.id.daxDialogOption1)?.setOnClickListener { onOptionClicked.invoke(options[0]) }
            ctaView?.findViewById<DaxButton>(R.id.daxDialogOption2)?.setOnClickListener { onOptionClicked.invoke(options[1]) }
            ctaView?.findViewById<DaxButton>(R.id.daxDialogOption3)?.setOnClickListener { onOptionClicked.invoke(options[2]) }
            ctaView?.findViewById<DaxButton>(R.id.daxDialogOption4)?.setOnClickListener { onOptionClicked.invoke(options[3]) }
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

    data class ExperimentDaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentDaxBubbleOptionsCta(
        CtaId.DAX_END,
        R.string.onboardingEndDaxDialogTitle,
        R.string.onboardingEndDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_END_CTA,
        onboardingStore,
        appInstallStore,
    )

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
                        "how to say duck in spanish",
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
}

interface ExperimentDaxCta {
    fun showOnboardingCta(
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit,
    )

    fun hideOnboardingCta(
        binding: FragmentBrowserTabBinding,
    )
}

sealed class ExperimentOnboardingDaxDialogCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int?,
    @StringRes open val buttonText: Int?,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ExperimentDaxCta, DaxCta {

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    override fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        binding.includeOnboardingDaxDialogExperiment.root.gone()
    }

    internal fun setOnboardingDialogView(
        daxText: String,
        buttonText: String?,
        binding: FragmentBrowserTabBinding,
        onTypingAnimationFinished: () -> Unit = {},
    ) {
        val daxDialog = binding.includeOnboardingDaxDialogExperiment

        daxDialog.root.show()
        daxDialog.dialogTextCta.text = ""
        daxDialog.hiddenTextCta.text = daxText.html(binding.root.context)
        buttonText?.let {
            daxDialog.primaryCta.show()
            daxDialog.primaryCta.alpha = MIN_ALPHA
            daxDialog.primaryCta.text = buttonText
        } ?: daxDialog.primaryCta.gone()
        binding.includeOnboardingDaxDialogExperiment.onboardingDialogSuggestionsContent.gone()
        binding.includeOnboardingDaxDialogExperiment.onboardingDialogContent.show()
        daxDialog.root.alpha = MAX_ALPHA
        daxDialog.dialogTextCta.startTypingAnimation(daxText, true) {
            ViewCompat.animate(daxDialog.primaryCta).alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION
            onTypingAnimationFinished.invoke()
        }
    }

    class DaxSerpCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.onboardingSerpDaxDialogDescription,
        R.string.onboardingSerpDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_SERP_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                buttonText = buttonText?.let { context.getString(it) },
                binding = binding,
            )
            binding.includeOnboardingDaxDialogExperiment.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
        }
    }

    class DaxTrackersBlockedCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val trackers: List<Entity>,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        null,
        R.string.onboardingTrackersBlockedDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = getTrackersDescription(context, trackers),
                buttonText = buttonText?.let { context.getString(it) },
                binding = binding,
                onTypingAnimationFinished = onTypingAnimationFinished,
            )
            binding.includeOnboardingDaxDialogExperiment.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
        }

        private fun getTrackersDescription(
            context: Context,
            trackersEntities: List<Entity>,
        ): String {
            val trackers = trackersEntities
                .map { it.displayName }
                .distinct()

            val trackersFiltered = trackers.take(MAX_TRACKERS_SHOWS)
            val trackersText = trackersFiltered.joinToString(", ")
            val size = trackers.size - trackersFiltered.size
            val quantityString =
                if (size == 0) {
                    context.resources.getQuantityString(R.plurals.onboardingTrackersBlockedZeroDialogDescription, trackersFiltered.size)
                } else {
                    context.resources.getQuantityString(R.plurals.onboardingTrackersBlockedDialogDescription, size, size)
                }
            return "<b>$trackersText</b>$quantityString"
        }
    }

    class DaxMainNetworkCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val network: String,
        private val siteHost: String,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_NETWORK,
        null,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NETWORK_CTA_1,
        onboardingStore,
        appInstallStore,
    ) {

        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = getTrackersDescription(context),
                buttonText = buttonText?.let { context.getString(it) },
                binding = binding,
            )
            binding.includeOnboardingDaxDialogExperiment.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
        }

        @VisibleForTesting
        fun getTrackersDescription(context: Context): String {
            return if (isFromSameNetworkDomain()) {
                context.resources.getString(
                    R.string.daxMainNetworkCtaText,
                    network,
                    Uri.parse(siteHost).baseHost?.removePrefix("m."),
                    network,
                )
            } else {
                context.resources.getString(
                    R.string.daxMainNetworkOwnedCtaText,
                    network,
                    Uri.parse(siteHost).baseHost?.removePrefix("m."),
                    network,
                )
            }
        }

        private fun isFromSameNetworkDomain(): Boolean = mainTrackerDomains.any { siteHost.contains(it) }
    }

    class DaxNoTrackersCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_OTHER,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NO_TRACKERS_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            setOnboardingDialogView(
                daxText = description?.let { context.getString(it) }.orEmpty(),
                buttonText = buttonText?.let { context.getString(it) },
                binding = binding,
            )
            binding.includeOnboardingDaxDialogExperiment.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
        }
    }

    class DaxFireButtonCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_FIRE_BUTTON,
        R.string.onboardingFireButtonDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_FIRE_DIALOG_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            val daxDialog = binding.includeOnboardingDaxDialogExperiment
            val daxText = description?.let { context.getString(it) }.orEmpty()

            daxDialog.primaryCta.gone()
            daxDialog.dialogTextCta.text = ""
            daxDialog.hiddenTextCta.text = daxText.html(binding.root.context)
            TransitionManager.beginDelayedTransition(binding.includeOnboardingDaxDialogExperiment.cardView, AutoTransition())
            daxDialog.dialogTextCta.startTypingAnimation(daxText, true)
        }
    }

    class DaxSiteSuggestionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : ExperimentOnboardingDaxDialogCta(
        CtaId.DAX_INTRO_VISIT_SITE,
        R.string.onboardingSitesDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val context = binding.root.context
            val daxDialog = binding.includeOnboardingDaxDialogExperiment
            val daxText = description?.let { context.getString(it) }.orEmpty()

            binding.includeOnboardingDaxDialogExperiment.onboardingDialogContent.gone()
            binding.includeOnboardingDaxDialogExperiment.onboardingDialogSuggestionsContent.show()
            daxDialog.suggestionsDialogTextCta.text = ""
            daxDialog.suggestionsHiddenTextCta.text = daxText.html(context)
            TransitionManager.beginDelayedTransition(binding.includeOnboardingDaxDialogExperiment.cardView, AutoTransition())
            daxDialog.suggestionsDialogTextCta.startTypingAnimation(daxText, true) {
                val optionsViews = listOf<DaxButton>(
                    daxDialog.daxDialogOption1,
                    daxDialog.daxDialogOption2,
                    daxDialog.daxDialogOption3,
                    daxDialog.daxDialogOption4,
                )

                optionsViews.forEachIndexed { index, buttonView ->
                    val options = DaxDialogIntroOption.getSitesOptions()
                    options[index].setOptionView(buttonView)
                    ViewCompat.animate(buttonView).alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION
                }
            }
        }

        fun setOnOptionClicked(
            daxDialog: IncludeExperimentViewDaxDialogBinding,
            onOptionClicked: (DaxDialogIntroOption) -> Unit,
        ) {
            val options = DaxDialogIntroOption.getSitesOptions()
            daxDialog.daxDialogOption1.setOnClickListener { onOptionClicked.invoke(options[0]) }
            daxDialog.daxDialogOption2.setOnClickListener { onOptionClicked.invoke(options[1]) }
            daxDialog.daxDialogOption3.setOnClickListener { onOptionClicked.invoke(options[2]) }
            daxDialog.daxDialogOption4.setOnClickListener { onOptionClicked.invoke(options[3]) }
        }
    }

    companion object {
        private const val MAX_TRACKERS_SHOWS = 2
        private val mainTrackerDomains = listOf("facebook", "google")
        private const val DAX_DIALOG_APPEARANCE_ANIMATION = 400L
        private const val MAX_ALPHA = 1.0f
        private const val MIN_ALPHA = 0.0f
    }
}
