/*
 * Copyright (c) 2019 DuckDuckGo
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
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeOnboardingBubbleBuckDialogBinding
import com.duckduckgo.app.browser.databinding.IncludeOnboardingInContextBuckDialogBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.cta.ui.DaxCta.Companion.MAX_DAYS_ALLOWED
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_WEBSITE_BROKEN
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_FIRE_DIALOG_CTA
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html
import com.google.android.material.button.MaterialButton

interface ViewCta {
    fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    )
}

interface DaxCta {
    val onboardingStore: OnboardingStore
    val appInstallStore: AppInstallStore
    var ctaPixelParam: String
    val markAsReadOnShow: Boolean
        get() = false

    companion object {
        const val MAX_DAYS_ALLOWED = 3
    }
}

interface Cta {
    val ctaId: CtaId
    val shownPixel: Pixel.PixelName?
    val okPixel: Pixel.PixelName?
    val cancelPixel: Pixel.PixelName?
    val closePixel: Pixel.PixelName?

    fun pixelShownParameters(): Map<String, String>
    fun pixelCancelParameters(): Map<String, String>
    fun pixelOkParameters(): Map<String, String>
}

interface OnboardingDaxCta {
    fun showOnboardingCta(
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onSecondaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit,
        onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)? = null,
        onDismissCtaClicked: () -> Unit,
    )

    fun hideOnboardingCta(
        binding: FragmentBrowserTabBinding,
    )
}

sealed class OnboardingDaxDialogCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int?,
    @StringRes open val buttonText: Int?,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override val closePixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, DaxCta, OnboardingDaxCta {

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    override fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        binding.includeOnboardingInContextDaxDialog.root.gone()
    }

    fun hideBuckOnboardingCta(binding: FragmentBrowserTabBinding) {
        binding.includeOnboardingInContextBuckDialog.root.gone()
    }

    internal fun setOnboardingDialogView(
        daxTitle: String? = null,
        daxText: String,
        primaryCtaText: String?,
        secondaryCtaText: String? = null,
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onSecondaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit = {},
        onDismissCtaClicked: () -> Unit,
    ) {
        val daxDialog = binding.includeOnboardingInContextDaxDialog

        daxDialog.root.show()
        binding.includeOnboardingInContextDaxDialog.primaryCta.setOnClickListener(null)
        binding.includeOnboardingInContextDaxDialog.secondaryCta.setOnClickListener(null)
        daxDialog.dialogTextCta.text = ""
        daxDialog.hiddenTextCta.text = daxText.html(binding.root.context)
        daxTitle?.let {
            daxDialog.onboardingDialogTitle.show()
            daxDialog.onboardingDialogTitle.text = daxTitle
        } ?: daxDialog.onboardingDialogTitle.gone()
        primaryCtaText?.let {
            daxDialog.primaryCta.show()
            daxDialog.primaryCta.alpha = MIN_ALPHA
            daxDialog.primaryCta.text = primaryCtaText
        } ?: daxDialog.primaryCta.gone()
        secondaryCtaText?.let {
            daxDialog.secondaryCta.show()
            daxDialog.secondaryCta.alpha = MIN_ALPHA
            daxDialog.secondaryCta.text = secondaryCtaText
        } ?: daxDialog.secondaryCta.gone()
        daxDialog.onboardingDialogSuggestionsContent.gone()
        daxDialog.onboardingDialogContent.show()
        daxDialog.root.alpha = MAX_ALPHA
        TransitionManager.beginDelayedTransition(daxDialog.cardView, AutoTransition())
        val afterAnimation = {
            daxDialog.dialogTextCta.finishAnimation()
            primaryCtaText?.let { daxDialog.primaryCta.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION }
            secondaryCtaText?.let { daxDialog.secondaryCta.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION }
            binding.includeOnboardingInContextDaxDialog.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
            binding.includeOnboardingInContextDaxDialog.secondaryCta.setOnClickListener { onSecondaryCtaClicked.invoke() }
            binding.includeOnboardingInContextDaxDialog.daxDialogDismissButton.setOnClickListener { onDismissCtaClicked.invoke() }
            onTypingAnimationFinished.invoke()
        }
        daxDialog.dialogTextCta.startTypingAnimation(daxText, true) { afterAnimation() }
        daxDialog.onboardingDaxDialogBackground.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogContent.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogSuggestionsContent.setOnClickListener { afterAnimation() }
    }

    internal fun setBuckOnboardingDialogView(
        title: String? = null,
        message: String,
        primaryCtaText: String?,
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onDismissCtaClicked: () -> Unit,
    ) {
        val binding = binding.includeOnboardingInContextBuckDialog

        with(binding) {
            root.show()
            primaryCta.setOnClickListener(null)

            val parsedMessage = message.html(binding.root.context)
            dialogTextCta.text = parsedMessage

            title?.let {
                with(onboardingDialogTitle) {
                    show()
                    text = title
                }
            } ?: onboardingDialogTitle.gone()

            primaryCtaText?.let {
                with(primaryCta) {
                    show()
                    text = primaryCtaText
                }
            } ?: primaryCta.gone()

            root.alpha = MAX_ALPHA

            primaryCta.setOnClickListener {
                onPrimaryCtaClicked.invoke()
            }

            daxDialogDismissButton.setOnClickListener {
                onDismissCtaClicked.invoke()
            }

            if (onboardingDaxDialogContainer.isVisible &&
                (onboardingDialogContent.isVisible || onboardingDialogSuggestionsContent.isVisible)
            ) {
                onboardingDialogContent.gone()
                onboardingDialogSuggestionsContent.gone()
                TransitionManager.beginDelayedTransition(buckOnboardingDialogView, AutoTransition())
                onboardingDialogContent.show()
            } else {
                onboardingDaxDialogContainer.show()
                onboardingDialogContent.show()
                animateBuckOnboardingDialogEntrance()
            }
        }
    }

    class DaxSerpCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.highlightsOnboardingSerpDaxDialogDescription,
        R.string.onboardingSerpDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_SERP_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
        }
    }

    class DaxTrackersBlockedCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val trackers: List<Entity>,
        val settingsDataStore: SettingsDataStore,
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        null,
        R.string.onboardingTrackersBlockedDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = getTrackersDescription(context, trackers),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = getTrackersDescription(context, trackers),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
        }

        @VisibleForTesting
        fun getTrackersDescription(
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
                        .getStringForOmnibarPosition(settingsDataStore.omnibarPosition)
                } else {
                    context.resources.getQuantityString(R.plurals.onboardingTrackersBlockedDialogDescription, size, size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarPosition)
                }
            return "<b>$trackersText</b>$quantityString"
        }
    }

    class DaxMainNetworkCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val network: String,
        private val siteHost: String,
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_NETWORK,
        null,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_NETWORK_CTA_1,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = getTrackersDescription(context),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = getTrackersDescription(context),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
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
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_OTHER,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_NO_TRACKERS_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
        }
    }

    class DaxFireButtonCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_FIRE_BUTTON,
        R.string.onboardingFireButtonDaxDialogDescription,
        R.string.onboardingFireButtonDaxDialogOkButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        DAX_FIRE_DIALOG_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = context.getString(R.string.onboardingFireButtonDaxDialogOkButton),
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = context.getString(R.string.onboardingFireButtonDaxDialogOkButton),
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
        }
    }

    class DaxSiteSuggestionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        private val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_INTRO_VISIT_SITE,
        R.string.onboardingSitesDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                showBuckOnboardingCta(
                    binding = binding,
                    onSuggestedOptionClicked = onSuggestedOptionClicked,
                )
            } else {
                val context = binding.root.context
                val daxDialog = binding.includeOnboardingInContextDaxDialog
                val daxText = description?.let { context.getString(it) }.orEmpty()

                binding.includeOnboardingInContextDaxDialog.onboardingDialogContent.gone()
                binding.includeOnboardingInContextDaxDialog.onboardingDialogSuggestionsContent.show()
                daxDialog.suggestionsDialogTextCta.text = ""
                daxDialog.suggestionsHiddenTextCta.text = daxText.html(context)
                TransitionManager.beginDelayedTransition(binding.includeOnboardingInContextDaxDialog.cardView, AutoTransition())
                val afterAnimation = {
                    onTypingAnimationFinished()
                    val optionsViews = listOf<DaxButton>(
                        daxDialog.daxDialogOption1,
                        daxDialog.daxDialogOption2,
                        daxDialog.daxDialogOption3,
                        daxDialog.daxDialogOption4,
                    )

                    optionsViews.forEachIndexed { index, buttonView ->
                        val options = onboardingStore.getSitesOptions()
                        options[index].setOptionView(buttonView)
                        buttonView.animate().alpha(MAX_ALPHA).duration = DAX_DIALOG_APPEARANCE_ANIMATION
                    }

                    val options = onboardingStore.getSitesOptions()
                    daxDialog.daxDialogOption1.setOnClickListener { onSuggestedOptionClicked?.invoke(options[0]) }
                    daxDialog.daxDialogOption2.setOnClickListener { onSuggestedOptionClicked?.invoke(options[1]) }
                    daxDialog.daxDialogOption3.setOnClickListener { onSuggestedOptionClicked?.invoke(options[2]) }
                    daxDialog.daxDialogOption4.setOnClickListener { onSuggestedOptionClicked?.invoke(options[3]) }
                }
                daxDialog.suggestionsDialogTextCta.startTypingAnimation(daxText, true) { afterAnimation() }
                daxDialog.onboardingDialogContent.setOnClickListener {
                    daxDialog.dialogTextCta.finishAnimation()
                    afterAnimation()
                }
            }
        }

        private fun showBuckOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
        ) {
            val context = binding.root.context
            val buckDialogBinding = binding.includeOnboardingInContextBuckDialog

            with(buckDialogBinding) {
                root.show()
                val message = description?.let { context.getString(it) }.orEmpty()
                val parsedDaxText = message.html(context)

                suggestionsDialogTextCta.text = parsedDaxText

                val optionsViews = listOf(
                    daxDialogOption1,
                    daxDialogOption2,
                    daxDialogOption3,
                    daxDialogOption4,
                )

                val options = onboardingStore.getSitesOptions()

                optionsViews.forEachIndexed { index, buttonView ->
                    options[index].setOptionView(buttonView)
                    buttonView.setOnClickListener {
                        onboardingDaxDialogContainer.gone()
                        onboardingDialogSuggestionsContent.gone()
                        onSuggestedOptionClicked?.invoke(options[index])
                    }
                }

                if (onboardingDaxDialogContainer.isVisible &&
                    (onboardingDialogContent.isVisible || onboardingDialogSuggestionsContent.isVisible)
                ) {
                    onboardingDialogContent.gone()
                    onboardingDialogSuggestionsContent.gone()
                    TransitionManager.beginDelayedTransition(buckDialogBinding.buckOnboardingDialogView, AutoTransition())
                    onboardingDialogSuggestionsContent.show()
                } else {
                    onboardingDaxDialogContainer.show()
                    onboardingDialogSuggestionsContent.show()
                    animateBuckOnboardingDialogEntrance()
                }
            }
        }
    }

    class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_END,
        R.string.highlightsOnboardingEndDaxDialogDescription,
        R.string.highlightsOnboardingEndDaxDialogButton,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        Pixel.PixelValues.DAX_ONBOARDING_END_CTA,
        onboardingStore,
        appInstallStore,
    ) {
        override val markAsReadOnShow: Boolean = true

        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            val context = binding.root.context

            if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
                setBuckOnboardingDialogView(
                    message = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            } else {
                setOnboardingDialogView(
                    daxText = description?.let { context.getString(it) }.orEmpty(),
                    primaryCtaText = buttonText?.let { context.getString(it) },
                    binding = binding,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                    onDismissCtaClicked = onDismissCtaClicked,
                )
            }
        }
    }

    internal fun IncludeOnboardingInContextBuckDialogBinding.animateBuckOnboardingDialogEntrance() {
        daxDialogDismissButton.alpha = MIN_ALPHA

        with(buckOnboardingDialogView) {
            alpha = MIN_ALPHA
            animateEntrance(
                onAnimationEnd = {
                    with(daxDialogDismissButton) {
                        show()
                        animate().apply {
                            alpha(MAX_ALPHA)
                            duration = DAX_DIALOG_APPEARANCE_ANIMATION
                        }.start()
                    }
                },
            )
        }
    }

    companion object {

        const val SERP = "duckduckgo"
        val mainTrackerNetworks = listOf("Facebook", "Google")

        private const val MAX_TRACKERS_SHOWS = 2
        private val mainTrackerDomains = listOf("facebook", "google")
        private const val DAX_DIALOG_APPEARANCE_ANIMATION = 400L
        private const val MAX_ALPHA = 1.0f
        private const val MIN_ALPHA = 0.0f
    }
}

sealed class DaxBubbleCta(
    override val ctaId: CtaId,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @DrawableRes open val placeholder: Int? = null,
    open val options: List<DaxDialogIntroOption>? = null,
    @StringRes open val primaryCta: Int? = null,
    @StringRes open val secondaryCta: Int? = null,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName? = null,
    override val closePixel: Pixel.PixelName? = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ViewCta, DaxCta {

    private var ctaView: View? = null

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        ctaView = view
        clearDialog()
        val daxTitle = view.context.getString(title)
        val daxText = view.context.getString(description)
        val optionsViews: List<DaxButton> = listOf(
            view.findViewById(R.id.daxDialogOption1),
            view.findViewById(R.id.daxDialogOption2),
            view.findViewById(R.id.daxDialogOption3),
            view.findViewById(R.id.daxDialogOption4),
        )

        primaryCta?.let {
            view.findViewById<DaxButton>(R.id.primaryCta).show()
            view.findViewById<DaxButton>(R.id.primaryCta).alpha = 0f
            view.findViewById<DaxButton>(R.id.primaryCta).text = view.context.getString(it)
        }

        secondaryCta?.let {
            view.findViewById<DaxButton>(R.id.secondaryCta).show()
            view.findViewById<DaxButton>(R.id.secondaryCta).alpha = 0f
            view.findViewById<DaxButton>(R.id.secondaryCta).text = view.context.getString(it)
        }

        placeholder?.let {
            view.findViewById<ImageView>(R.id.placeholder).show()
            view.findViewById<ImageView>(R.id.placeholder).alpha = 0f
            view.findViewById<ImageView>(R.id.placeholder).setImageResource(it)
        }

        options?.let {
            optionsViews.forEachIndexed { index, buttonView ->
                buttonView.show()
                if (it.size > index) {
                    it[index].setOptionView(buttonView)
                } else {
                    buttonView.gone()
                }
            }
        }

        TransitionManager.beginDelayedTransition(view.findViewById(R.id.cardView), AutoTransition())
        view.show()
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).text = ""
        view.findViewById<DaxTextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(R.id.daxBubbleDialogTitle).apply {
            alpha = 0f
            text = daxTitle.html(view.context)
        }
        val afterAnimation = {
            view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).finishAnimation()
            view.findViewById<ImageView>(R.id.placeholder).animate().alpha(1f).setDuration(500)
            view.findViewById<DaxButton>(R.id.primaryCta).animate().alpha(1f).setDuration(500)
            view.findViewById<DaxButton>(R.id.secondaryCta).animate().alpha(1f).setDuration(500)
            options?.let {
                optionsViews.forEachIndexed { index, buttonView ->
                    if (it.size > index) {
                        buttonView.animate().alpha(1f).setDuration(500)
                    }
                }
            }
            onTypingAnimationFinished()
        }

        view.animate().alpha(1f).setDuration(500).setStartDelay(600).withEndAction {
            view.findViewById<DaxTextView>(R.id.daxBubbleDialogTitle).animate().alpha(1f).setDuration(500)
                .withEndAction {
                    view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true) {
                        afterAnimation()
                    }
                }
        }
        view.findViewById<View>(R.id.cardContainer).setOnClickListener { afterAnimation() }
    }

    fun showBuckCta(
        binding: IncludeOnboardingBubbleBuckDialogBinding,
        configuration: DaxBubbleCta,
        onAnimationEnd: () -> Unit,
    ) {
        ctaView = binding.root
        clearBubbleBuckDialog(binding)
        val daxTitle = binding.root.context.getString(title)
        val daxText = binding.root.context.getString(description)
        val optionsViews: List<MaterialButton> = listOf(
            binding.daxDialogOption1,
            binding.daxDialogOption2,
            binding.daxDialogOption3,
            binding.daxDialogOption4,
        )

        primaryCta?.let { primaryCtaRes ->
            with(binding.primaryCta) {
                show()
                text = binding.root.context.getString(primaryCtaRes)
            }
        }

        secondaryCta?.let { secondaryCtaRes ->
            with(binding.secondaryCta) {
                show()
                text = binding.root.context.getString(secondaryCtaRes)
            }
        }

        placeholder?.let { placeholderImageRes ->
            with(binding.placeholder) {
                show()
                setImageResource(placeholderImageRes)
            }
        }

        options?.let { options ->
            optionsViews.forEachIndexed { index, buttonView ->
                if (options.size > index) {
                    options[index].setOptionView(buttonView)
                    buttonView.show()
                } else {
                    buttonView.gone()
                }
            }
        }

        if (configuration is DaxEndCta) {
            binding.root.updatePadding(bottom = 8.toPx())
            binding.buckOnboardingDialogView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 0.toPx()
            }
        }

        with(binding) {
            dialogTextCta.text = daxText.html(root.context)
            daxBubbleDialogTitle.text = daxTitle.html(root.context)

            binding.root.show()
            buckOnboardingDialogView.animateEntrance(
                onAnimationEnd = {
                    daxDialogDismissButton.animate().alpha(1f).setDuration(500)
                    onAnimationEnd()
                },
            )
        }
    }

    private fun clearDialog() {
        ctaView?.findViewById<DaxButton>(R.id.primaryCta)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.primaryCta)?.gone()
        ctaView?.findViewById<DaxButton>(R.id.secondaryCta)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.secondaryCta)?.gone()
        ctaView?.findViewById<ImageView>(R.id.placeholder)?.alpha = 0f
        ctaView?.findViewById<ImageView>(R.id.placeholder)?.gone()
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption1)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption1)?.gone()
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption2)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption2)?.gone()
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption3)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption3)?.gone()
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption4)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(R.id.daxDialogOption4)?.gone()
    }

    private fun clearBubbleBuckDialog(binding: IncludeOnboardingBubbleBuckDialogBinding) {
        binding.apply {
            this.daxDialogDismissButton.alpha = 0f
            this.primaryCta.gone()
            this.secondaryCta.gone()
            this.placeholder.gone()
            this.daxDialogOption1.gone()
            this.daxDialogOption2.gone()
            this.daxDialogOption3.gone()
            this.daxDialogOption4.gone()
        }
    }

    fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(R.id.primaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(R.id.secondaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<View>(R.id.daxDialogDismissButton)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    fun setOnOptionClicked(onOptionClicked: (DaxDialogIntroOption) -> Unit) {
        options?.forEachIndexed { index, option ->
            val optionView = when (index) {
                0 -> R.id.daxDialogOption1
                1 -> R.id.daxDialogOption2
                2 -> R.id.daxDialogOption3
                else -> R.id.daxDialogOption4
            }
            option.let { ctaView?.findViewById<MaterialButton>(optionView)?.setOnClickListener { onOptionClicked.invoke(option) } }
        }
    }

    fun hideDaxBubbleCta(
        binding: FragmentBrowserTabBinding,
        onboardingDesignExperimentToggles: OnboardingDesignExperimentToggles,
    ) {
        if (onboardingDesignExperimentToggles.buckOnboarding().isEnabled()) {
            binding.includeNewBrowserTab.includeOnboardingBuckDialogBubble.root.gone()
        } else {
            binding.includeNewBrowserTab.includeOnboardingDaxDialogBubble.root.gone()
        }
    }

    override val markAsReadOnShow: Boolean = true

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    data class DaxIntroSearchOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO,
        title = R.string.onboardingSearchDaxDialogTitle,
        description = R.string.highlightsOnboardingSearchDaxDialogDescription,
        options = onboardingStore.getExperimentSearchOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxIntroVisitSiteOptionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO_VISIT_SITE,
        title = R.string.onboardingSitesDaxDialogTitle,
        description = R.string.onboardingSitesDaxDialogDescription,
        options = onboardingStore.getSitesOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_VISIT_SITE_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_END,
        title = R.string.onboardingEndDaxDialogTitle,
        description = R.string.highlightsOnboardingEndDaxDialogDescription,
        primaryCta = R.string.highlightsOnboardingEndDaxDialogButton,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxPrivacyProCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val titleRes: Int,
        val descriptionRes: Int,
        val primaryCtaRes: Int,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO_PRIVACY_PRO,
        title = titleRes,
        description = descriptionRes,
        placeholder = com.duckduckgo.mobile.android.R.drawable.ic_privacy_pro_128,
        primaryCta = primaryCtaRes,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_PRIVACY_PRO,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxDialogIntroOption(
        val optionText: String,
        @DrawableRes val iconRes: Int,
        val link: String,
    ) {
        fun setOptionView(buttonView: MaterialButton) {
            buttonView.apply {
                text = optionText
                icon = ContextCompat.getDrawable(this.context, iconRes)
            }
        }
    }
}

sealed class HomePanelCta(
    override val ctaId: CtaId,
    @DrawableRes open val image: Int,
    @StringRes open val title: Int,
    @StringRes open val description: Int,
    @StringRes open val okButton: Int,
    @StringRes open val dismissButton: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override val closePixel: Pixel.PixelName? = null,
) : Cta, ViewCta {

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        // no-op. We are now using a Bottom Sheet to display this
        // but we want to keep the same classes for pixels, etc
    }

    override fun pixelCancelParameters(): Map<String, String> = emptyMap()

    override fun pixelOkParameters(): Map<String, String> = emptyMap()

    override fun pixelShownParameters(): Map<String, String> = emptyMap()

    // Base class to hold the shared configuration. This is temporary, for an experiment.
    open class AddWidgetAutoBase : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaAutoLaunchButton,
        R.string.addWidgetCtaDismissButton,
        AppPixelName.WIDGET_CTA_SHOWN,
        AppPixelName.WIDGET_CTA_LAUNCHED,
        AppPixelName.WIDGET_CTA_DISMISSED,
    )

    object AddWidgetAuto : AddWidgetAutoBase()

    object AddWidgetAutoOnboardingExperiment : AddWidgetAutoBase()

    object AddWidgetInstructions : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaInstructionsLaunchButton,
        R.string.addWidgetCtaDismissButton,
        AppPixelName.WIDGET_LEGACY_CTA_SHOWN,
        AppPixelName.WIDGET_LEGACY_CTA_LAUNCHED,
        AppPixelName.WIDGET_LEGACY_CTA_DISMISSED,
    )
}

class BrokenSitePromptDialogCta : Cta {

    override val ctaId: CtaId = CtaId.BROKEN_SITE_PROMPT
    override val shownPixel: Pixel.PixelName = SITE_NOT_WORKING_SHOWN
    override val okPixel: Pixel.PixelName = SITE_NOT_WORKING_WEBSITE_BROKEN
    override val cancelPixel: Pixel.PixelName? = null
    override val closePixel: Pixel.PixelName? = null

    override fun pixelCancelParameters(): Map<String, String> = mapOf()

    override fun pixelOkParameters(): Map<String, String> = mapOf()

    override fun pixelShownParameters(): Map<String, String> = mapOf()

    fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        val view = binding.includeBrokenSitePromptDialog.root
        view.gone()
    }

    fun showBrokenSitePromptCta(
        binding: FragmentBrowserTabBinding,
        onReportBrokenSiteClicked: () -> Unit,
        onDismissCtaClicked: () -> Unit,
        onCtaShown: () -> Unit,
    ) {
        val daxDialog = binding.includeBrokenSitePromptDialog
        daxDialog.root.show()
        binding.includeBrokenSitePromptDialog.reportButton.setOnClickListener { onReportBrokenSiteClicked.invoke() }
        binding.includeBrokenSitePromptDialog.dismissButton.setOnClickListener { onDismissCtaClicked.invoke() }
        onCtaShown()
    }
}

fun DaxCta.addCtaToHistory(newCta: String): String {
    val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
    val daysInstalled = minOf(appInstallStore.daysInstalled().toInt(), MAX_DAYS_ALLOWED)
    param.add("$newCta:$daysInstalled")
    val finalParam = param.joinToString("-")
    onboardingStore.onboardingDialogJourney = finalParam
    return finalParam
}

fun DaxCta.canSendShownPixel(): Boolean {
    val param = onboardingStore.onboardingDialogJourney?.split("-").orEmpty().toMutableList()
    return !(param.isNotEmpty() && param.any { it.split(":").firstOrNull().orEmpty() == ctaPixelParam })
}

fun String.getStringForOmnibarPosition(position: OmnibarPosition): String {
    return when (position) {
        OmnibarPosition.TOP -> this
        OmnibarPosition.BOTTOM -> replace("☝", "\uD83D\uDC47")
    }
}
