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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.cta.ui.DaxCta.Companion.MAX_DAYS_ALLOWED
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.view.DaxTypeAnimationTextView
import com.duckduckgo.app.onboarding.ui.view.TouchInterceptingLinearLayout
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_WEBSITE_BROKEN
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_FIRE_DIALOG_CTA
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.shape.DaxOnboardingBubbleBrandDesignUpdateCardView
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.device.isTablet
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import com.google.android.material.button.MaterialButton
import kotlin.collections.forEachIndexed
import kotlin.collections.toMutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import com.duckduckgo.mobile.android.R as DesignSystemR

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

    fun hideOnboardingCta(binding: FragmentBrowserTabBinding)
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
) : Cta,
    DaxCta,
    OnboardingDaxCta {
    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    override fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
        binding.includeOnboardingInContextDaxDialog.root.gone()
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
        onDismissCtaClicked: (() -> Unit)?,
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
        daxDialog.daxDialogDismissButton.isVisible = onDismissCtaClicked != null
        TransitionManager.beginDelayedTransition(daxDialog.cardView, AutoTransition())
        val afterAnimation = {
            daxDialog.dialogTextCta.finishAnimation()
            primaryCtaText?.let {
                daxDialog.primaryCta
                    .animate()
                    .alpha(MAX_ALPHA)
                    .duration = DAX_DIALOG_APPEARANCE_ANIMATION
            }
            secondaryCtaText?.let {
                daxDialog.secondaryCta
                    .animate()
                    .alpha(MAX_ALPHA)
                    .duration = DAX_DIALOG_APPEARANCE_ANIMATION
            }
            binding.includeOnboardingInContextDaxDialog.primaryCta.setOnClickListener { onPrimaryCtaClicked.invoke() }
            binding.includeOnboardingInContextDaxDialog.secondaryCta.setOnClickListener { onSecondaryCtaClicked.invoke() }
            daxDialog.daxDialogDismissButton.setOnClickListener(onDismissCtaClicked?.let { { it() } })
            onTypingAnimationFinished.invoke()
        }
        daxDialog.dialogTextCta.startTypingAnimation(daxText, true) { afterAnimation() }
        daxDialog.onboardingDaxDialogBackground.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogContent.setOnClickListener { afterAnimation() }
        daxDialog.onboardingDialogSuggestionsContent.setOnClickListener { afterAnimation() }
    }

    class DaxSerpCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.onboardingSerpDaxDialogDescription,
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

    class DaxTrackersBlockedCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val trackers: List<Entity>,
        val settingsDataStore: SettingsDataStore,
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

        @VisibleForTesting
        fun getTrackersDescription(
            context: Context,
            trackersEntities: List<Entity>,
        ): String {
            val trackers =
                trackersEntities
                    .map { it.displayName }
                    .distinct()

            val trackersFiltered = trackers.take(MAX_TRACKERS_SHOWS)
            val trackersText = trackersFiltered.joinToString(", ")
            val size = trackers.size - trackersFiltered.size
            val quantityString =
                if (size == 0) {
                    context.resources
                        .getQuantityString(R.plurals.onboardingTrackersBlockedZeroDialogDescription, trackersFiltered.size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarType)
                } else {
                    context.resources
                        .getQuantityString(R.plurals.onboardingTrackersBlockedDialogDescription, size, size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarType)
                }
            return "<b>$trackersText</b>$quantityString"
        }
    }

    class DaxMainNetworkCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val network: String,
        private val siteHost: String,
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

        @VisibleForTesting
        fun getTrackersDescription(context: Context): String =
            if (isFromSameNetworkDomain()) {
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

        private fun isFromSameNetworkDomain(): Boolean = mainTrackerDomains.any { siteHost.contains(it) }
    }

    class DaxNoTrackersCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
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

    class DaxFireButtonCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
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

    class DaxSiteSuggestionsCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        private val onSiteSuggestionOptionClicked: (index: Int) -> Unit, // used to fire experiment pixel
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

                val optionsViews =
                    listOf<DaxButton>(
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

    class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_END,
        R.string.onboardingEndDaxDialogDescription,
        R.string.onboardingEndDaxDialogButton,
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

    class DaxDuckAiFireButtonCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_DUCK_AI_FIRE_BUTTON,
        R.string.onboardingDuckAiFireButtonDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
        "duck_ai_fire_button_cta",
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
            setOnboardingDialogView(
                daxTitle = context.getString(R.string.onboardingDuckAiFireButtonDaxDialogTitle),
                daxText = description?.let { context.getString(it) }.orEmpty(),
                primaryCtaText = null,
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
                onDismissCtaClicked = null, // no dismiss button
            )
        }
    }

    /**
     * Base class for the brand-design rebrand of [OnboardingDaxDialogCta]. Owns the render
     * pipeline so subclasses only need to declare their active content include and populate it.
     *
     * Mirrors the structure of [DaxBubbleCta.BrandDesignUpdateBubbleCta] but targets the
     * contextual in-browser dialog layout (`include_onboarding_in_context_dax_dialog_brand_design_update.xml`).
     *
     * Subclasses supply:
     *  - [activeIncludeId]: the id of the single content-include slot to show for this CTA
     *  - [configureContentViews]: populate title, description, and the active include's children
     *  - [setOnPrimaryCtaClicked] / [setOnSecondaryCtaClicked] / [setOnOptionClicked]: override only
     *    for the buttons the subclass actually renders.
     */

    interface ShowsWingBottom

    abstract class BrandDesignContextualDaxDialogCta(
        ctaId: CtaId,
        @StringRes description: Int?,
        @StringRes buttonText: Int?,
        shownPixel: Pixel.PixelName?,
        okPixel: Pixel.PixelName?,
        cancelPixel: Pixel.PixelName?,
        closePixel: Pixel.PixelName?,
        ctaPixelParam: String,
        onboardingStore: OnboardingStore,
        appInstallStore: AppInstallStore,
        open val isLightTheme: Boolean,
        open val deviceInfo: DeviceInfo,
        @DrawableRes open val backgroundRes: Int = 0,
    ) : OnboardingDaxDialogCta(
        ctaId = ctaId,
        description = description,
        buttonText = buttonText,
        shownPixel = shownPixel,
        okPixel = okPixel,
        cancelPixel = cancelPixel,
        closePixel = closePixel,
        ctaPixelParam = ctaPixelParam,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    ) {

        protected var ctaView: View? = null

        private var runningFadeIn: AnimatorSet? = null
        private var runningFadeOut: AnimatorSet? = null
        private var arrowDepthAnimator: ValueAnimator? = null
        private var cardContainer: TouchInterceptingLinearLayout? = null

        private var isAnimating: Boolean = false
            set(value) {
                field = value
                cardContainer?.interceptChildTouches = value
            }

        /** Id of the content-include slot this CTA renders (e.g. [R.id.contextualBrandDesignPrimaryCtaContent]). */
        abstract val activeIncludeId: Int

        abstract val showArrow: Boolean

        /**
         * Populate the card with subclass-specific content: set title/description text, configure
         * option buttons, etc. Called before the card fade-in begins so all text is set while views
         * have `alpha=0` to avoid visible growth.
         *
         * Primary-CTA button text is applied by the base class from [buttonText] before this runs;
         * subclasses do not need to set it.
         */
        abstract fun configureContentViews(view: View)

        /**
         * Hook invoked exactly once after the typing animation has fully settled (natural end or
         * tap-to-skip). Default is a no-op.
         *
         * **Only override when this CTA must trigger the privacy-shield highlight that the legacy
         * `DaxTrackersBlockedCta` triggers.** The fragment unconditionally passes
         * [onTypingAnimationFinished] so the highlight gating lives here, in the subclass — not at
         * the call site. Overriding for any other reason will incorrectly fire the privacy-shield
         * highlight from a non-trackers CTA.
         */
        protected open fun onTypingAnimationSettled(onTypingAnimationFinished: () -> Unit) {
            // No-op by default — see kdoc for the override contract.
        }

        override fun hideOnboardingCta(binding: FragmentBrowserTabBinding) {
            cancelRunningAnimations()
            hideContainer(binding)
            ctaView = null
            cardContainer = null
        }

        private fun cancelRunningAnimations() {
            isAnimating = false
            runningFadeIn?.removeAllListeners()
            runningFadeIn?.cancel()
            runningFadeIn = null
            runningFadeOut?.removeAllListeners()
            runningFadeOut?.cancel()
            runningFadeOut = null
            arrowDepthAnimator?.removeAllUpdateListeners()
            arrowDepthAnimator?.cancel()
            arrowDepthAnimator = null
            wingPlayInGeneration++
            ctaView?.animate()?.cancel()
            ctaView?.let { bannerFor(it)?.cancel() }
            ctaView?.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
                ?.cancelAnimation()
        }

        override fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
        ) {
            showOnboardingCta(
                binding = binding,
                onPrimaryCtaClicked = onPrimaryCtaClicked,
                onSecondaryCtaClicked = onSecondaryCtaClicked,
                onTypingAnimationFinished = onTypingAnimationFinished,
                onSuggestedOptionClicked = onSuggestedOptionClicked,
                onDismissCtaClicked = onDismissCtaClicked,
                instantShow = false,
            )
        }

        fun showOnboardingCta(
            binding: FragmentBrowserTabBinding,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
            instantShow: Boolean,
        ) {
            val container = binding.includeOnboardingInContextDaxDialogBrandDesign.root
            val isContentTransition = isContentTransition(container)
            ctaView = container

            cancelRunningAnimations()

            if (instantShow) {
                showInstantly(
                    container = container,
                    onPrimaryCtaClicked = onPrimaryCtaClicked,
                    onSecondaryCtaClicked = onSecondaryCtaClicked,
                    onSuggestedOptionClicked = onSuggestedOptionClicked,
                    onDismissCtaClicked = onDismissCtaClicked,
                    onTypingAnimationFinished = onTypingAnimationFinished,
                )
                return
            }

            val titleView = container.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
            val descriptionView = container.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription)
            val dismissButton = container.findViewById<ImageView>(R.id.contextualBrandDesignDismissButton)
            val cardContainer = container.findViewById<TouchInterceptingLinearLayout>(R.id.contextualBrandDesignCardContainer)
            val cardView = container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.contextualBrandDesignCardView)
            val targetDepth = if (showArrow && !container.isPhoneLandscape()) 1f else 0f
            this.cardContainer = cardContainer
            isAnimating = true

            val activeInclude = container.findViewById<View>(activeIncludeId)

            val notifySettled = {
                if (isAnimating) {
                    isAnimating = false
                    onTypingAnimationSettled(onTypingAnimationFinished)
                }
            }

            val typeAndFadeIn = {
                bannerFor(container)?.slideIn()
                startWingBottomPlayIn(container)
                val daxTitle = titleView.text?.toString().orEmpty()
                val startContentFadeIn = {
                    val animators = mutableListOf<Animator>(
                        ObjectAnimator.ofFloat(descriptionView, View.ALPHA, 1f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                        ObjectAnimator.ofFloat(activeInclude, View.ALPHA, 1f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                    )
                    // Dismiss button is persistent: fade it in only if it isn't already fully shown,
                    // which covers both the first-show path (alpha=0) and the case where a previous
                    // animation was cancelled mid-flight leaving it at a fractional alpha.
                    if (dismissButton.alpha < 1f) {
                        animators += ObjectAnimator.ofFloat(dismissButton, View.ALPHA, 1f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                    }
                    val currentDepth = cardView.arrowDepthFraction
                    if (targetDepth != currentDepth) {
                        arrowDepthAnimator = ValueAnimator.ofFloat(currentDepth, targetDepth).apply {
                            duration = DIALOG_CONTENT_FADE_IN_DURATION
                            interpolator = FastOutSlowInInterpolator()
                            addUpdateListener { cardView.setArrowDepthFraction(it.animatedValue as Float) }
                        }
                        animators.add(arrowDepthAnimator!!)
                    }
                    runningFadeIn = AnimatorSet().apply {
                        playTogether(animators.toList())
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                notifySettled()
                            }
                        })
                        start()
                    }
                }
                if (daxTitle.isEmpty()) {
                    startContentFadeIn()
                } else {
                    titleView.alpha = 1f
                    titleView.text = ""

                    titleView.typingDelayInMs = TYPING_DELAY_MS
                    titleView.delayAfterAnimationInMs = TYPING_POST_DELAY_MS
                    titleView.startTypingAnimation(daxTitle, true) {
                        startContentFadeIn()
                    }
                }
            }

            if (isContentTransition) {
                // Content transition: fade out old description + any visible content include, then swap in the new
                val allContentIncludes = getAllContentIncludes(container)
                val fadeOutAnimators = mutableListOf<Animator>(
                    ObjectAnimator.ofFloat(descriptionView, View.ALPHA, 0f)
                        .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                )
                allContentIncludes.forEach { include ->
                    if (include.isVisible && include.alpha > 0f) {
                        fadeOutAnimators += ObjectAnimator.ofFloat(include, View.ALPHA, 0f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                    }
                }
                bannerFor(container)?.slideOut()?.let { fadeOutAnimators += it }
                if (container.alpha < 1f) {
                    fadeOutAnimators += ObjectAnimator.ofFloat(container, View.ALPHA, 1f)
                        .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                }
                runningFadeOut = AnimatorSet().apply {
                    playTogether(fadeOutAnimators.toList())
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            applyContent(container, isContentTransition = true)
                            if (isAnimating) {
                                typeAndFadeIn()
                            }
                        }
                    })
                    start()
                }
            } else {
                applyContent(container, isContentTransition = false)
                container.show()
                container.animate().alpha(1f).setDuration(DIALOG_FADE_IN_DURATION).setStartDelay(DIALOG_FADE_IN_START_DELAY)
                    .withEndAction {
                        if (isAnimating) {
                            typeAndFadeIn()
                        }
                    }
            }

            // Tap-to-skip: any tap on the dialog area (card or surrounding backdrop) ends running
            // animations and snaps all content visible — matches the legacy onboarding behaviour
            // where the whole screen is the skip surface, not just the card.
            container.setOnClickListener {
                snapToFinished(
                    container = container,
                    titleView = titleView,
                    descriptionView = descriptionView,
                    dismissButton = dismissButton,
                    activeInclude = activeInclude,
                    cardContainer = cardContainer,
                    alreadySettled = !isAnimating,
                    contentFadeInAnimator = runningFadeIn,
                    fadeOutAnimator = runningFadeOut,
                    onSettled = { notifySettled() },
                )
            }

            setOnPrimaryCtaClicked(onPrimaryCtaClicked)
            setOnSecondaryCtaClicked(onSecondaryCtaClicked)
            setOnOptionClicked(onSuggestedOptionClicked)
            setOnDismissCtaClicked(onDismissCtaClicked)
        }

        private fun showInstantly(
            container: View,
            onPrimaryCtaClicked: () -> Unit,
            onSecondaryCtaClicked: () -> Unit,
            onSuggestedOptionClicked: ((DaxDialogIntroOption) -> Unit)?,
            onDismissCtaClicked: () -> Unit,
            onTypingAnimationFinished: () -> Unit,
        ) {
            val titleView = container.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
            val descriptionView = container.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription)
            val dismissButton = container.findViewById<ImageView>(R.id.contextualBrandDesignDismissButton)
            val cardContainer = container.findViewById<TouchInterceptingLinearLayout>(R.id.contextualBrandDesignCardContainer)
            val activeInclude = container.findViewById<View>(activeIncludeId)

            applyContent(container, isContentTransition = false)
            container.alpha = 1f
            container.show()

            bannerFor(container)?.snapToFinalPosition()

            // No animation to skip — clear any stale tap-to-skip listener from a prior animated show.
            container.setOnClickListener(null)
            setOnPrimaryCtaClicked(onPrimaryCtaClicked)
            setOnSecondaryCtaClicked(onSecondaryCtaClicked)
            setOnOptionClicked(onSuggestedOptionClicked)
            setOnDismissCtaClicked(onDismissCtaClicked)

            snapToFinished(
                container = container,
                titleView = titleView,
                descriptionView = descriptionView,
                dismissButton = dismissButton,
                activeInclude = activeInclude,
                cardContainer = cardContainer,
                alreadySettled = false,
                contentFadeInAnimator = null,
                fadeOutAnimator = null,
                onSettled = { onTypingAnimationSettled(onTypingAnimationFinished) },
            )
        }

        /**
         * Per-show content setup: reset shared state, populate text via [configureContentViews],
         * and stage the background. Used by all three show paths (first-show, content transition,
         * rotation re-inflate). What follows is path-specific: animate, snap, or animate-after-fadeout.
         */
        private fun applyContent(container: View, isContentTransition: Boolean) {
            val titleView = container.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
            val hiddenTitle = container.findViewById<DaxTextView>(R.id.contextualBrandDesignHiddenTitle)
            val activeInclude = container.findViewById<View>(activeIncludeId)

            resetSharedViewState(container, isContentTransition = isContentTransition)
            resetAllIncludesExcept(container, activeInclude)
            applyPrimaryCtaText(container)
            configureContentViews(container)
            applyWingBottomState(container)
            hiddenTitle.text = titleView.text
            applyTitleSlotVisibility(container, titleView)
            bannerFor(container)?.show()
            applyOptionsContentHeight(container)
        }

        private fun stripWingForPhoneLandscape(wing: LottieAnimationView) {
            if (wing.isAnimating) wing.cancelAnimation()
            wing.isVisible = false
        }

        internal fun applyWingBottomState(container: View) {
            wingPlayInGeneration++
            val wing = container.findViewById<LottieAnimationView>(R.id.wingBottom) ?: return
            if (container.isPhoneLandscape()) {
                stripWingForPhoneLandscape(wing)
                return
            }
            (wing.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.startToStart =
                    if (wing.isTablet()) R.id.contextualBrandDesignCardView else ConstraintLayout.LayoutParams.PARENT_ID
                wing.layoutParams = lp
            }
            val showsWing = this is ShowsWingBottom
            when {
                showsWing && !wing.isVisible -> {
                    // Stage only; [startWingBottomPlayIn] kicks off playback after the banner slides in.
                    wing.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                    wing.progress = 0f
                    wing.isVisible = true
                }
                showsWing && wing.isVisible -> {
                    // Persist across same-wing content transitions: leave at resting state, no replay.
                    // Abort any in-flight exit from a prior non-wing CTA — its end-listener would otherwise hide the wing.
                    if (wing.isAnimating && wing.progress >= WING_STOP_PROGRESS) {
                        wing.removeAllAnimatorListeners()
                        wing.cancelAnimation()
                        wing.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                        wing.progress = WING_STOP_PROGRESS
                    }
                }
                !showsWing && wing.isVisible -> {
                    wing.setMinAndMaxProgress(WING_STOP_PROGRESS, 1f)
                    wing.progress = WING_STOP_PROGRESS
                    wing.addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            wing.isVisible = false
                            wing.removeAnimatorListener(this)
                        }
                    })
                    wing.playAnimation()
                }
                else -> wing.isVisible = false
            }
        }

        internal fun startWingBottomPlayIn(container: View) {
            if (this !is ShowsWingBottom) return
            val wing = container.findViewById<LottieAnimationView>(R.id.wingBottom) ?: return
            if (container.isPhoneLandscape()) {
                stripWingForPhoneLandscape(wing)
                return
            }
            if (!wing.isVisible || wing.isAnimating || wing.progress >= WING_STOP_PROGRESS) return
            val generation = wingPlayInGeneration
            wing.postDelayed(
                {
                    if (wingPlayInGeneration != generation) return@postDelayed
                    if (wing.isVisible && !wing.isAnimating && wing.progress < WING_STOP_PROGRESS) {
                        wing.playAnimation()
                    }
                },
                WING_PLAY_IN_DELAY,
            )
        }

        private fun snapWingBottomToResting(container: View) {
            if (this !is ShowsWingBottom) return
            val wing = container.findViewById<LottieAnimationView>(R.id.wingBottom) ?: return
            if (container.isPhoneLandscape()) {
                stripWingForPhoneLandscape(wing)
                return
            }
            if (wing.isAnimating) wing.cancelAnimation()
            wing.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
            wing.progress = WING_STOP_PROGRESS
            wing.isVisible = true
        }

        private fun applyOptionsContentHeight(container: View) {
            if (activeIncludeId != R.id.contextualBrandDesignOptionsContent) return
            val resources = container.resources
            val capHeight = resources.getBoolean(R.bool.capContextualOptionsHeight)
            container.findViewById<View>(R.id.contextualBrandDesignOptionsContent)
                ?.updateLayoutParams {
                    height = if (capHeight) {
                        resources.getDimensionPixelSize(R.dimen.contextualOptionsCappedHeight)
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
        }

        /**
         * Snaps title/description/dismiss/active-include to their final visible state.
         *
         * Split out as a testable helper so unit tests can exercise the state machine
         * (title-before-animation, mid-animation, post-animation, rapid double-tap).
         */
        internal fun snapToFinished(
            container: View,
            titleView: DaxTypeAnimationTextView,
            descriptionView: DaxTextView,
            dismissButton: ImageView,
            activeInclude: View,
            cardContainer: TouchInterceptingLinearLayout,
            alreadySettled: Boolean,
            contentFadeInAnimator: AnimatorSet?,
            fadeOutAnimator: AnimatorSet?,
            onSettled: () -> Unit,
        ) {
            this.cardContainer = cardContainer
            isAnimating = false
            fadeOutAnimator?.let { if (it.isRunning) it.cancel() }
            titleView.finishAnimation()
            // If typing hasn't started yet (tap during initial fade-in), set title directly
            // so we don't show an empty title. Restore alpha to 1 for CTAs that do have a title;
            // empty-title CTAs are unaffected visually since there is no text to render.
            val hiddenTitle = container.findViewById<DaxTextView>(R.id.contextualBrandDesignHiddenTitle)
            if (!titleView.hasAnimationStarted()) {
                titleView.text = hiddenTitle.text
            }
            if (!hiddenTitle.text.isNullOrEmpty()) {
                titleView.alpha = 1f
            }
            descriptionView.alpha = 1f
            dismissButton.alpha = 1f
            activeInclude.alpha = 1f
            container.alpha = 1f
            bannerFor(container)?.snapToFinalPosition()
            contentFadeInAnimator?.let { if (it.isRunning) it.end() }
            container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.contextualBrandDesignCardView)
                ?.setArrowDepthFraction(if (showArrow && !container.isPhoneLandscape()) 1f else 0f)
            snapWingBottomToResting(container)
            if (!alreadySettled) {
                onSettled()
            }
        }

        private fun applyPrimaryCtaText(container: View) {
            val text = buttonText ?: return
            container.findViewById<DaxButtonPrimary>(R.id.contextualBrandDesignPrimaryCta)?.setText(text)
        }

        // GONE (not INVISIBLE) so the FrameLayout's marginBottom drops out of the LinearLayout
        // flow, leaving the description sitting at the card's top padding.
        private fun applyTitleSlotVisibility(container: View, titleView: DaxTypeAnimationTextView) {
            val titleIsEmpty = titleView.text?.toString().orEmpty().isEmpty()
            container.findViewById<View>(R.id.contextualBrandDesignTitleSlot)?.visibility =
                if (titleIsEmpty) View.GONE else View.VISIBLE
        }

        private fun bannerFor(container: View): BackgroundBanner? {
            val view = container.findViewById<ImageView>(R.id.contextualBrandDesignBackground) ?: return null
            return BackgroundBanner(view, backgroundRes)
        }

        /**
         * Slide-up banner that sits behind the contextual card. Scoped to a single CTA show:
         * construct per-call and discard. State is read from the view (visibility, translationY)
         * so callers don't need to thread flags through.
         */
        internal class BackgroundBanner(
            private val view: ImageView,
            @DrawableRes private val res: Int,
        ) {
            val isShowing: Boolean get() = view.isVisible

            /** Stage the banner offscreen, ready for [slideIn] to bring it up. */
            fun show() {
                if (res == 0) return
                view.setImageResource(res)
                view.visibility = View.VISIBLE
                view.doOnPreDraw { it.translationY = offScreenY() }
            }

            /**
             * Snap the banner to its final on-screen position. Registers in the same pre-draw
             * pass as [show]'s offscreen offset; the later registration wins so the banner lands
             * fully on-screen on the first frame (used by the instantShow path on rotation).
             */
            fun snapToFinalPosition() {
                if (res == 0) return
                view.doOnPreDraw { it.translationY = 0f }
            }

            fun slideIn() {
                if (!isShowing) return
                view.animate()
                    .translationY(0f)
                    .setDuration(SLIDE_DURATION)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            fun slideOut(): Animator? {
                if (!isShowing || res == 0) return null
                return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, offScreenY())
                    .setDuration(SLIDE_DURATION)
            }

            fun cancel() {
                view.animate().cancel()
            }

            private fun offScreenY(): Float {
                val parent = view.parent as? View
                return if (parent != null) (parent.height - view.top).toFloat() else view.height.toFloat()
            }

            companion object {
                private const val SLIDE_DURATION = 300L
            }
        }

        /**
         * True when the brand-design layout is already mounted from a previous CTA. Drives shared-view
         * persistence (e.g. the dismiss button) so a content swap doesn't snap them to alpha=0.
         */
        internal fun isContentTransition(container: View): Boolean =
            container.alpha > 0f && container.isVisible

        /**
         * Reset every mutable property shared views may carry over from a previous CTA. Called at
         * the start of both first-show and mid-transition flows.
         *
         * Title alpha resets to 0 — subclasses that render a title set it to 1 via [typeAndFadeIn]
         * before the typing animation runs. CTAs with no title leave the title view at alpha=0 so
         * an empty typing animation never plays.
         *
         * The dismiss button is a persistent UI control: on first-show ([isContentTransition] = false)
         * it is reset to alpha=0 so the post-typing fade-in reveals it together with the description;
         * on a content transition the dialog stays on screen so the dismiss button must remain
         * visible — we leave its alpha untouched.
         */
        internal fun resetSharedViewState(container: View, isContentTransition: Boolean) {
            // Skip on content transitions so the slide-out animator can drive the banner off-screen.
            if (!isContentTransition) {
                container.findViewById<View>(R.id.contextualBrandDesignBackground)?.visibility = View.GONE
            }
            container.findViewById<View>(R.id.contextualBrandDesignTitleSlot)?.visibility = View.VISIBLE
            container.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)?.apply {
                alpha = 0f
                text = ""
            }
            container.findViewById<DaxTextView>(R.id.contextualBrandDesignHiddenTitle)?.apply {
                // Hidden title is android:visibility="invisible" in XML — alpha is not rendered.
                // It acts as a text cache for snapToFinished before the typing animation starts.
                text = ""
            }
            container.findViewById<DaxTextView>(R.id.contextualBrandDesignDescription)?.apply {
                alpha = 0f
                text = ""
            }
            if (!isContentTransition) {
                container.findViewById<View>(R.id.contextualBrandDesignDismissButton)?.alpha = 0f
                container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.contextualBrandDesignCardView)
                    ?.setArrowDepthFraction(0f)
            }
            container.findViewById<View>(R.id.wavingDax)?.visibility = View.GONE
        }

        protected open val allContentIncludeIds: List<Int> = listOf(
            R.id.contextualBrandDesignPrimaryCtaContent,
            R.id.contextualBrandDesignOptionsContent,
        )

        /** Returns all content-include slots in the card. Used to hide inactive includes. */
        internal fun getAllContentIncludes(view: View): List<View> =
            allContentIncludeIds.mapNotNull { view.findViewById(it) }

        internal fun resetAllIncludesExcept(view: View, active: View) {
            getAllContentIncludes(view).forEach { include ->
                if (include == active) {
                    include.show()
                    include.alpha = 0f
                } else {
                    include.gone()
                }
            }
        }

        /** No-op by default. Subclasses with a primary CTA button override this. */
        open fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
            // No-op.
        }

        /** No-op by default. Subclasses with a secondary CTA button override this. */
        open fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
            // No-op.
        }

        /** No-op by default. Subclasses with option buttons override this. */
        open fun setOnOptionClicked(onOptionClicked: ((DaxDialogIntroOption) -> Unit)?) {
            // No-op.
        }

        private fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
            ctaView?.findViewById<View>(R.id.contextualBrandDesignDismissButton)?.setOnClickListener {
                onButtonClicked.invoke()
            }
        }

        protected fun View.isTablet(): Boolean = deviceInfo.isTablet()

        protected fun View.isPhoneLandscape(): Boolean =
            !deviceInfo.isTablet() &&
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        companion object {
            private const val DIALOG_FADE_IN_DURATION = 400L
            private const val DIALOG_FADE_IN_START_DELAY = 200L
            private const val DIALOG_CONTENT_FADE_IN_DURATION = 200L
            private const val TYPING_DELAY_MS = 20L
            private const val TYPING_POST_DELAY_MS = 20L
            private const val WING_STOP_PROGRESS = 0.5f
            private const val WING_PLAY_IN_DELAY = 300L

            // Shared across CTA instances because the fragment swaps contextual CTAs without
            // calling hideOnboardingCta on the previous one — a per-instance ref couldn't
            // reach the pending runnable posted on the shared wingBottom view.
            private var wingPlayInGeneration = 0

            /**
             * Cancels the title typing animation and hides the brand-design root, without
             * touching the AnimatorSet state owned by a CTA instance. Used by the fragment as
             * a no-instance fallback and by [hideOnboardingCta] after instance-level cleanup.
             */
            internal fun hideContainer(binding: FragmentBrowserTabBinding) {
                val root = binding.includeOnboardingInContextDaxDialogBrandDesign.root
                root.findViewById<DaxTypeAnimationTextView>(R.id.contextualBrandDesignTitle)
                    ?.cancelAnimation()
                root.gone()
            }
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
    @DrawableRes open val backgroundRes: Int = 0,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName? = null,
    override val closePixel: Pixel.PixelName? = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta,
    ViewCta,
    DaxCta {
    var isModifiedControlOnboardingExperimentEnabled: Boolean? = null

    protected var ctaView: View? = null

    override fun showCta(
        view: View,
        onTypingAnimationFinished: () -> Unit,
    ) {
        ctaView = view
        clearDialog()
        val daxTitle = view.context.getString(title)
        val daxText = view.context.getString(description)
        val optionsViews: List<DaxButton> =
            listOf(
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

        if (isModifiedControlOnboardingExperimentEnabled == true) {
            options?.let { options ->
                // modifiedControl has a max of 3 options to match other experiment variants
                val modifiedControlOptions =
                    options
                        .toMutableList()
                        .apply {
                            if (this@DaxBubbleCta is DaxIntroVisitSiteOptionsCta) {
                                removeAt(1) // Remove the regional news option
                            }
                        }.toList()

                optionsViews.forEachIndexed { index, buttonView ->
                    if (modifiedControlOptions.size > index) {
                        buttonView.show()
                        modifiedControlOptions[index].setOptionView(buttonView)
                    } else {
                        buttonView.gone()
                    }
                }
            }
        } else {
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
            view
                .findViewById<ImageView>(R.id.placeholder)
                .animate()
                .alpha(1f)
                .setDuration(500)
            view
                .findViewById<DaxButton>(R.id.primaryCta)
                .animate()
                .alpha(1f)
                .setDuration(500)
            view
                .findViewById<DaxButton>(R.id.secondaryCta)
                .animate()
                .alpha(1f)
                .setDuration(500)
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
            view
                .findViewById<DaxTextView>(R.id.daxBubbleDialogTitle)
                .animate()
                .alpha(1f)
                .setDuration(500)
                .withEndAction {
                    view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true) {
                        afterAnimation()
                    }
                }
        }
        view.findViewById<View>(R.id.cardContainer).setOnClickListener { afterAnimation() }
    }

    protected open fun clearDialog() {
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

    open fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(R.id.primaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    open fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(R.id.secondaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    open fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<View>(R.id.daxDialogDismissButton)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    open fun setOnOptionClicked(
        onboardingExperimentEnabled: Boolean = false,
        configuration: DaxBubbleCta? = null,
        onOptionClicked: (DaxDialogIntroOption, index: Int?) -> Unit,
    ) {
        if (onboardingExperimentEnabled && configuration is DaxIntroVisitSiteOptionsCta) {
            val optionsWithoutRegionalNews =
                options
                    ?.toMutableList()
                    ?.apply {
                        removeAt(1) // Remove the regional news option
                    }?.toList()

            optionsWithoutRegionalNews?.forEachIndexed { index, option ->
                val optionView =
                    when (index) {
                        0 -> R.id.daxDialogOption1
                        1 -> R.id.daxDialogOption2
                        2 -> R.id.daxDialogOption3
                        else -> R.id.daxDialogOption4 // This will not be visible for the experiments
                    }
                option.let { ctaView?.findViewById<MaterialButton>(optionView)?.setOnClickListener { onOptionClicked.invoke(option, index) } }
            }
        } else {
            options?.forEachIndexed { index, option ->
                val optionView =
                    when (index) {
                        0 -> R.id.daxDialogOption1
                        1 -> R.id.daxDialogOption2
                        2 -> R.id.daxDialogOption3
                        else -> R.id.daxDialogOption4
                    }
                option.let { ctaView?.findViewById<MaterialButton>(optionView)?.setOnClickListener { onOptionClicked.invoke(option, index) } }
            }
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
        description = R.string.onboardingSearchDaxDialogDescription,
        options = onboardingStore.getSearchOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    interface ShowsWavingDax {
        val restartWavingDax: Boolean get() = false
        fun configureWavingDax(dax: LottieAnimationView, deviceInfo: DeviceInfo) {
            val density = dax.resources.displayMetrics.density
            dax.rotation = 0f
            dax.translationX = DEFAULT_WAVING_DAX_TRANSLATION_X_DP * density
            dax.translationY = DEFAULT_WAVING_DAX_TRANSLATION_Y_DP * density
            (dax.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.startToStart =
                    if (deviceInfo.isTablet()) R.id.brandDesignCardView else ConstraintLayout.LayoutParams.PARENT_ID
                dax.layoutParams = lp
            }
        }

        companion object {
            private const val DEFAULT_WAVING_DAX_TRANSLATION_X_DP = -54f
            private const val DEFAULT_WAVING_DAX_TRANSLATION_Y_DP = -110f
        }
    }

    abstract class BrandDesignUpdateBubbleCta(
        ctaId: CtaId,
        @StringRes title: Int,
        @StringRes description: Int,
        options: List<DaxDialogIntroOption>? = null,
        @DrawableRes backgroundRes: Int = 0,
        shownPixel: Pixel.PixelName?,
        okPixel: Pixel.PixelName?,
        ctaPixelParam: String,
        onboardingStore: OnboardingStore,
        appInstallStore: AppInstallStore,
        open val isLightTheme: Boolean,
        open val deviceInfo: DeviceInfo,
    ) : DaxBubbleCta(
        ctaId = ctaId,
        title = title,
        description = description,
        options = options,
        backgroundRes = backgroundRes,
        shownPixel = shownPixel,
        okPixel = okPixel,
        ctaPixelParam = ctaPixelParam,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    ) {

        protected fun View.isTablet(): Boolean = deviceInfo.isTablet()

        protected fun View.isPhoneLandscape(): Boolean =
            !deviceInfo.isTablet() &&
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        companion object {
            private const val DIALOG_FADE_IN_DURATION = 400L
            private const val DIALOG_CONTENT_FADE_IN_DURATION = 200L
            private const val HEADER_IMAGE_FADE_IN_DURATION = 300L
            private const val ARROW_DEPTH_ANIMATION_DURATION = 200L
            private const val TYPING_DELAY_MS = 20L
            private const val TYPING_POST_DELAY_MS = 20L
            private const val DISMISS_BORDER_WIDTH_DP = 1.5f
        }

        abstract val activeIncludeId: Int

        abstract val showArrow: Boolean

        abstract fun configureContentViews(view: View)

        private var cardContainer: TouchInterceptingLinearLayout? = null

        private var isAnimating: Boolean = false
            set(value) {
                field = value
                cardContainer?.interceptChildTouches = value
            }

        private var contentFadeInAnimator: AnimatorSet? = null
        private var fadeOutAnimator: AnimatorSet? = null
        private var arrowDepthAnimator: ValueAnimator? = null

        protected fun resolveOnboardingContext(context: Context): Context {
            val themeRes = if (isLightTheme) {
                DesignSystemR.style.Theme_DuckDuckGo_Light_Onboarding
            } else {
                DesignSystemR.style.Theme_DuckDuckGo_Dark_Onboarding
            }
            return ContextThemeWrapper(context, themeRes)
        }

        private fun styleDismissButton(button: ImageView) {
            val themedContext = resolveOnboardingContext(button.context)
            val bgColor = themedContext.getColorFromAttr(DesignSystemR.attr.onboardingSurfaceTertiary)
            val borderColor = themedContext.getColorFromAttr(DesignSystemR.attr.onboardingAccentAltPrimary)
            val iconColor = themedContext.getColorFromAttr(DesignSystemR.attr.onboardingIconsPrimary)

            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(DISMISS_BORDER_WIDTH_DP.toPx().toInt(), borderColor)
            }
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(iconColor))
        }

        private fun getAllContentIncludes(view: View): List<View> = listOfNotNull(
            view.findViewById<View>(R.id.optionsContent),
            view.findViewById<View>(R.id.primaryCta),
        )

        internal fun applyWavingDaxState(container: View, showsWavingDax: ShowsWavingDax?) {
            container.findViewById<LottieAnimationView>(R.id.wavingDax)?.let { dax ->
                if (showsWavingDax != null && !container.isPhoneLandscape()) {
                    showsWavingDax.configureWavingDax(dax, deviceInfo)
                    if (!dax.isVisible || dax.alpha == 0f) {
                        dax.progress = 0f
                        dax.alpha = 1f
                        dax.isVisible = true
                        dax.post { dax.playAnimation() }
                    }
                } else {
                    dax.isVisible = false
                }
            }
        }

        private fun resetAllIncludesExcept(view: View, active: View) {
            getAllContentIncludes(view).forEach { include ->
                if (include == active) {
                    include.show()
                    include.alpha = 0f
                } else {
                    include.gone()
                }
            }
        }

        override fun showCta(
            container: View,
            onTypingAnimationFinished: () -> Unit,
        ) {
            ctaView = container

            cancelRunningAnimations()
            val isContentTransition = container.alpha > 0f && container.isVisible // card already visible from previous CTA

            val cardView = container.findViewById<DaxOnboardingBubbleBrandDesignUpdateCardView>(R.id.brandDesignCardView)
            val targetDepth = if (showArrow && !container.isPhoneLandscape()) 1f else 0f

            val daxTitle = container.context.getString(title)
            val daxDescription = container.context.getString(description).preventWidows()

            val titleView = container.findViewById<DaxTypeAnimationTextView>(R.id.brandDesignTitle)
            val hiddenTitle = container.findViewById<DaxTextView>(R.id.brandDesignHiddenTitle)
            val descriptionView = container.findViewById<DaxTextView>(R.id.brandDesignDescription)
            val dismissButton = container.findViewById<ImageView>(R.id.brandDesignDismissButton)
            val headerImage = container.findViewById<ImageView>(R.id.brandDesignHeaderImage)
            styleDismissButton(dismissButton)
            cardContainer = container.findViewById<TouchInterceptingLinearLayout>(R.id.brandDesignCardContainer)
            isAnimating = true

            // The active content include for THIS CTA
            val activeInclude = container.findViewById<View>(activeIncludeId)

            // Hides the header between CTAs; subclasses that use it re-enable
            // visibility inside configureContentViews().
            val resetHeaderState = {
                headerImage?.isVisible = false
                headerImage?.alpha = 0f
            }

            val resetTextAlignment = {
                titleView.gravity = Gravity.START
                hiddenTitle.gravity = Gravity.START
                descriptionView.gravity = Gravity.START
            }

            val wavingDax = this as? ShowsWavingDax

            // Helper: type title then fade in content
            val typeAndFadeIn = {
                hiddenTitle.text = daxTitle.html(container.context)
                descriptionView.text = daxDescription.html(container.context)

                val startTyping = {
                    titleView.alpha = 1f
                    titleView.text = ""

                    titleView.typingDelayInMs = TYPING_DELAY_MS
                    titleView.delayAfterAnimationInMs = TYPING_POST_DELAY_MS
                    titleView.startTypingAnimation(daxTitle, true) {
                        val animators = mutableListOf<Animator>(
                            ObjectAnimator.ofFloat(descriptionView, View.ALPHA, 1f)
                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                            ObjectAnimator.ofFloat(dismissButton, View.ALPHA, 1f)
                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                            ObjectAnimator.ofFloat(activeInclude, View.ALPHA, 1f)
                                .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                        )
                        // Read depth live: the first-show arm synchronously sets it before this lambda runs,
                        // so a value captured at function entry would animate from a stale snapshot.
                        val currentDepth = cardView.arrowDepthFraction
                        if (targetDepth != currentDepth) {
                            arrowDepthAnimator = ValueAnimator.ofFloat(currentDepth, targetDepth).apply {
                                duration = ARROW_DEPTH_ANIMATION_DURATION
                                interpolator = FastOutSlowInInterpolator()
                                addUpdateListener { cardView.setArrowDepthFraction(it.animatedValue as Float) }
                            }
                            animators.add(arrowDepthAnimator!!)
                        }
                        contentFadeInAnimator = AnimatorSet().apply {
                            playTogether(animators.toList())
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (isAnimating) {
                                        isAnimating = false
                                        onTypingAnimationFinished()
                                    }
                                }
                            })
                            start()
                        }
                    }
                }

                if (headerImage?.isVisible == true) {
                    headerImage.animate()
                        .alpha(1f)
                        .setDuration(HEADER_IMAGE_FADE_IN_DURATION)
                        .withEndAction {
                            // cancel() invokes withEndAction; skip typing when snapToFinished has
                            // already set the final state.
                            if (isAnimating) {
                                startTyping()
                            }
                        }
                } else {
                    startTyping()
                }
            }

            val applySettledState = {
                hiddenTitle.text = daxTitle.html(container.context)
                descriptionView.text = daxDescription.html(container.context)
                if (!titleView.hasAnimationStarted()) {
                    titleView.text = daxTitle.html(container.context)
                }
                titleView.alpha = 1f
                descriptionView.alpha = 1f
                dismissButton.alpha = 1f
                activeInclude.alpha = 1f
                if (headerImage?.isVisible == true) {
                    headerImage.alpha = 1f
                }
                cardView.setArrowDepthFraction(targetDepth)
            }

            if (isContentTransition) {
                // Content transition: fade out title + description + visible includes, then swap and animate new
                val allContentIncludes = getAllContentIncludes(container)
                val fadeOutAnimators = mutableListOf<Animator>(
                    ObjectAnimator.ofFloat(titleView, View.ALPHA, 0f)
                        .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                    ObjectAnimator.ofFloat(descriptionView, View.ALPHA, 0f)
                        .setDuration(DIALOG_CONTENT_FADE_IN_DURATION),
                )
                // Fade out any currently visible content include
                allContentIncludes.forEach { include ->
                    if (include.isVisible && include.alpha > 0f) {
                        fadeOutAnimators += ObjectAnimator.ofFloat(include, View.ALPHA, 0f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                    }
                }
                container.findViewById<LottieAnimationView>(R.id.wavingDax)?.let { dax ->
                    if (dax.isVisible && dax.alpha > 0f && (wavingDax == null || wavingDax.restartWavingDax)) {
                        fadeOutAnimators += ObjectAnimator.ofFloat(dax, View.ALPHA, 0f)
                            .setDuration(DIALOG_CONTENT_FADE_IN_DURATION)
                    }
                }
                fadeOutAnimator = AnimatorSet().apply {
                    playTogether(fadeOutAnimators.toList())
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // After fade-out: hide old includes, show new one.
                            // Note: do NOT call clearDialog() here — it would re-zero the dismiss
                            // button alpha causing a flicker. Instead, selectively reset content only.
                            resetAllIncludesExcept(container, activeInclude)
                            resetHeaderState()
                            resetTextAlignment()
                            configureContentViews(container)
                            applyWavingDaxState(container, wavingDax)
                            // Blank the title so typing (or snapped settled state) shows new text, not stale.
                            titleView.text = ""
                            if (!isAnimating) {
                                applySettledState()
                            } else {
                                typeAndFadeIn()
                            }
                        }
                    })
                    start()
                }
            } else {
                clearDialog()
                resetAllIncludesExcept(container, activeInclude)
                hiddenTitle.text = daxTitle.html(container.context)
                descriptionView.text = daxDescription.html(container.context)
                resetHeaderState()
                resetTextAlignment()
                configureContentViews(container)
                applyWavingDaxState(container, wavingDax)
                cardView.setArrowDepthFraction(targetDepth)
                container.show()
                container.animate().alpha(1f).setDuration(DIALOG_FADE_IN_DURATION).setStartDelay(200L)
                    .withEndAction {
                        if (isAnimating) {
                            typeAndFadeIn()
                        }
                    }
            }

            // Tap-to-skip: end running animations and snap all content visible
            fun snapToFinished() {
                // Set the flag before cancelling animators; cancel() fires end callbacks
                // (fadeOutAnimator.onAnimationEnd / headerImage withEndAction) which read it.
                val wasAnimating = isAnimating
                isAnimating = false
                titleView.finishAnimation()
                headerImage?.animate()?.cancel()
                val pendingFadeOut = fadeOutAnimator
                if (pendingFadeOut?.isRunning == true) {
                    // cancel() fires onAnimationEnd synchronously, which applies settled state via the branch above.
                    pendingFadeOut.cancel()
                } else {
                    applySettledState()
                }
                contentFadeInAnimator?.let { if (it.isRunning) it.end() }
                cardView.setArrowDepthFraction(targetDepth)
                if (wasAnimating) {
                    onTypingAnimationFinished()
                }
            }
            cardContainer?.setOnClickListener { snapToFinished() }
        }

        fun cancelRunningAnimations() {
            isAnimating = false
            contentFadeInAnimator?.removeAllListeners()
            contentFadeInAnimator?.cancel()
            contentFadeInAnimator = null
            fadeOutAnimator?.removeAllListeners()
            fadeOutAnimator?.cancel()
            fadeOutAnimator = null
            arrowDepthAnimator?.removeAllUpdateListeners()
            arrowDepthAnimator?.cancel()
            arrowDepthAnimator = null
            ctaView?.animate()?.cancel()
        }

        override fun clearDialog() {
            ctaView?.let { view ->
                view.findViewById<DaxTypeAnimationTextView>(R.id.brandDesignTitle)?.apply {
                    alpha = 1f
                    text = ""
                }
                view.findViewById<DaxTextView>(R.id.brandDesignDescription)?.alpha = 0f
                view.findViewById<View>(R.id.brandDesignDismissButton)?.alpha = 0f
                // Hide all content includes — include-level alpha/gone is sufficient;
                // children don't need individual alpha management since the parent
                // include's alpha controls their composite visibility.
                getAllContentIncludes(view).forEach { include ->
                    include.alpha = 0f
                    include.gone()
                }
            }
        }

        override fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
            ctaView?.findViewById<View>(R.id.brandDesignDismissButton)?.setOnClickListener {
                onButtonClicked.invoke()
            }
        }

        override fun setOnOptionClicked(
            onboardingExperimentEnabled: Boolean,
            configuration: DaxBubbleCta?,
            onOptionClicked: (DaxDialogIntroOption, index: Int?) -> Unit,
        ) {
            // No-op by default. Subclasses with option buttons override this.
        }
    }

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
        description = R.string.onboardingEndDaxDialogDescription,
        primaryCta = R.string.onboardingEndDaxDialogButton,
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_END_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

    data class DaxSubscriptionCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
        val isFreeTrialCopy: Boolean,
    ) : DaxBubbleCta(
        ctaId = CtaId.DAX_INTRO_PRIVACY_PRO,
        title = R.string.onboardingPrivacyProDaxDialogTitle,
        description = R.string.onboardingPrivacyProDaxDialogDescription,
        placeholder = DesignSystemR.drawable.ic_privacy_pro_128,
        primaryCta = if (isFreeTrialCopy) {
            R.string.onboardingPrivacyProDaxDialogFreeTrialOkButton
        } else {
            R.string.onboardingPrivacyProDaxDialogOkButton
        },
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_SUBSCRIPTION,
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
) : Cta,
    ViewCta {
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

    data object AddWidgetAutoOnboarding :
        HomePanelCta(
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

    data object AddWidgetInstructions : HomePanelCta(
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

enum class SubscriptionPromoFlow(
    val origin: String,
    val shownPixel: Pixel.PixelName,
    val subscribeClickPixel: Pixel.PixelName,
) {
    SKIPPED_ONBOARDING(
        "funnel_modal_android__skippedonboardingupsell",
        AppPixelName.SUBSCRIPTION_PROMO_MODAL_SKIPPED_ONBOARDING_SHOWN,
        AppPixelName.SUBSCRIPTION_PROMO_MODAL_SKIPPED_ONBOARDING_SUBSCRIBE_CLICKED,
    ),
    NUDGE(
        "funnel_modal_android__subscriptionnudge",
        AppPixelName.SUBSCRIPTION_PROMO_MODAL_NUDGE_SHOWN,
        AppPixelName.SUBSCRIPTION_PROMO_MODAL_NUDGE_SUBSCRIBE_CLICKED,
    ),
}

class SubscriptionPromoModalCta(
    val isFreeTrialCopy: Boolean,
    val flow: SubscriptionPromoFlow,
) : Cta {
    override val ctaId: CtaId = CtaId.DAX_INTRO_PRIVACY_PRO
    override val shownPixel: Pixel.PixelName = AppPixelName.ONBOARDING_DAX_CTA_SHOWN
    override val okPixel: Pixel.PixelName = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON
    override val cancelPixel: Pixel.PixelName = AppPixelName.ONBOARDING_DAX_CTA_DISMISS_BUTTON
    override val closePixel: Pixel.PixelName? = null

    private fun pixelParams(): Map<String, String> = mapOf(
        Pixel.PixelParameter.CTA_SHOWN to Pixel.PixelValues.MODAL_SUBSCRIPTION_CTA,
        Pixel.PixelParameter.FREE_TRIAL to isFreeTrialCopy.toString(),
    )

    override fun pixelShownParameters(): Map<String, String> = pixelParams()
    override fun pixelOkParameters(): Map<String, String> = pixelParams()
    override fun pixelCancelParameters(): Map<String, String> = pixelParams()
}

fun addCtaToHistory(
    onboardingStore: OnboardingStore,
    appInstallStore: AppInstallStore,
    newCta: String,
): String {
    val param =
        onboardingStore.onboardingDialogJourney
            ?.split("-")
            .orEmpty()
            .toMutableList()
    val daysInstalled = minOf(appInstallStore.daysInstalled().toInt(), MAX_DAYS_ALLOWED)
    param.add("$newCta:$daysInstalled")
    val finalParam = param.joinToString("-")
    onboardingStore.onboardingDialogJourney = finalParam
    return finalParam
}

fun DaxCta.addCtaToHistory(newCta: String): String =
    addCtaToHistory(onboardingStore, appInstallStore, newCta)

fun canSendShownPixel(
    onboardingStore: OnboardingStore,
    ctaPixelParam: String,
): Boolean {
    val param =
        onboardingStore.onboardingDialogJourney
            ?.split("-")
            .orEmpty()
            .toMutableList()
    return !(param.isNotEmpty() && param.any { it.split(":").firstOrNull().orEmpty() == ctaPixelParam })
}

fun DaxCta.canSendShownPixel(): Boolean =
    canSendShownPixel(onboardingStore, ctaPixelParam)

fun String.getStringForOmnibarPosition(position: OmnibarType): String =
    when (position) {
        OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> this
        OmnibarType.SINGLE_BOTTOM -> replace("☝", "\uD83D\uDC47")
    }

private fun View.fadeIn(duration: Duration = 500.milliseconds): ViewPropertyAnimator = animate().alpha(1f).setDuration(duration.inWholeMilliseconds)
