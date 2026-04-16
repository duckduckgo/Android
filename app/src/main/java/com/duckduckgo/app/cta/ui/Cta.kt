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
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.cta.ui.DaxCta.Companion.MAX_DAYS_ALLOWED
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.view.DaxTypeAnimationTextView
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.SITE_NOT_WORKING_WEBSITE_BROKEN
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_FIRE_DIALOG_CTA
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.mobile.android.R
import com.google.android.material.button.MaterialButton
import kotlin.collections.forEachIndexed
import kotlin.collections.toMutableList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import com.duckduckgo.app.browser.R as BrowserR

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
            binding.includeOnboardingInContextDaxDialog.daxDialogDismissButton.setOnClickListener { onDismissCtaClicked.invoke() }
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
        BrowserR.string.onboardingSerpDaxDialogDescription,
        BrowserR.string.onboardingSerpDaxDialogButton,
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
        BrowserR.string.onboardingTrackersBlockedDaxDialogButton,
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
                        .getQuantityString(BrowserR.plurals.onboardingTrackersBlockedZeroDialogDescription, trackersFiltered.size)
                        .getStringForOmnibarPosition(settingsDataStore.omnibarType)
                } else {
                    context.resources
                        .getQuantityString(BrowserR.plurals.onboardingTrackersBlockedDialogDescription, size, size)
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
        BrowserR.string.daxDialogGotIt,
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
                    BrowserR.string.daxMainNetworkCtaText,
                    network,
                    Uri.parse(siteHost).baseHost?.removePrefix("m."),
                    network,
                )
            } else {
                context.resources.getString(
                    BrowserR.string.daxMainNetworkOwnedCtaText,
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
        BrowserR.string.daxNonSerpCtaText,
        BrowserR.string.daxDialogGotIt,
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
        BrowserR.string.onboardingFireButtonDaxDialogDescription,
        BrowserR.string.onboardingFireButtonDaxDialogOkButton,
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
                primaryCtaText = context.getString(BrowserR.string.onboardingFireButtonDaxDialogOkButton),
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
        BrowserR.string.onboardingSitesDaxDialogDescription,
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
        BrowserR.string.onboardingEndDaxDialogDescription,
        BrowserR.string.onboardingEndDaxDialogButton,
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
                view.findViewById(BrowserR.id.daxDialogOption1),
                view.findViewById(BrowserR.id.daxDialogOption2),
                view.findViewById(BrowserR.id.daxDialogOption3),
                view.findViewById(BrowserR.id.daxDialogOption4),
            )

        primaryCta?.let {
            view.findViewById<DaxButton>(BrowserR.id.primaryCta).show()
            view.findViewById<DaxButton>(BrowserR.id.primaryCta).alpha = 0f
            view.findViewById<DaxButton>(BrowserR.id.primaryCta).text = view.context.getString(it)
        }

        secondaryCta?.let {
            view.findViewById<DaxButton>(BrowserR.id.secondaryCta).show()
            view.findViewById<DaxButton>(BrowserR.id.secondaryCta).alpha = 0f
            view.findViewById<DaxButton>(BrowserR.id.secondaryCta).text = view.context.getString(it)
        }

        placeholder?.let {
            view.findViewById<ImageView>(BrowserR.id.placeholder).show()
            view.findViewById<ImageView>(BrowserR.id.placeholder).alpha = 0f
            view.findViewById<ImageView>(BrowserR.id.placeholder).setImageResource(it)
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

        TransitionManager.beginDelayedTransition(view.findViewById(BrowserR.id.cardView), AutoTransition())
        view.show()
        view.findViewById<TypeAnimationTextView>(BrowserR.id.dialogTextCta).text = ""
        view.findViewById<DaxTextView>(BrowserR.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<DaxTextView>(BrowserR.id.daxBubbleDialogTitle).apply {
            alpha = 0f
            text = daxTitle.html(view.context)
        }
        val afterAnimation = {
            view.findViewById<TypeAnimationTextView>(BrowserR.id.dialogTextCta).finishAnimation()
            view
                .findViewById<ImageView>(BrowserR.id.placeholder)
                .animate()
                .alpha(1f)
                .setDuration(500)
            view
                .findViewById<DaxButton>(BrowserR.id.primaryCta)
                .animate()
                .alpha(1f)
                .setDuration(500)
            view
                .findViewById<DaxButton>(BrowserR.id.secondaryCta)
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
                .findViewById<DaxTextView>(BrowserR.id.daxBubbleDialogTitle)
                .animate()
                .alpha(1f)
                .setDuration(500)
                .withEndAction {
                    view.findViewById<TypeAnimationTextView>(BrowserR.id.dialogTextCta).startTypingAnimation(daxText, true) {
                        afterAnimation()
                    }
                }
        }
        view.findViewById<View>(BrowserR.id.cardContainer).setOnClickListener { afterAnimation() }
    }

    protected open fun clearDialog() {
        ctaView?.findViewById<DaxButton>(BrowserR.id.primaryCta)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.primaryCta)?.gone()
        ctaView?.findViewById<DaxButton>(BrowserR.id.secondaryCta)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.secondaryCta)?.gone()
        ctaView?.findViewById<ImageView>(BrowserR.id.placeholder)?.alpha = 0f
        ctaView?.findViewById<ImageView>(BrowserR.id.placeholder)?.gone()
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption1)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption1)?.gone()
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption2)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption2)?.gone()
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption3)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption3)?.gone()
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption4)?.alpha = 0f
        ctaView?.findViewById<DaxButton>(BrowserR.id.daxDialogOption4)?.gone()
    }

    open fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(BrowserR.id.primaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    open fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<MaterialButton>(BrowserR.id.secondaryCta)?.setOnClickListener {
            onButtonClicked.invoke()
        }
    }

    open fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
        ctaView?.findViewById<View>(BrowserR.id.daxDialogDismissButton)?.setOnClickListener {
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
                        0 -> BrowserR.id.daxDialogOption1
                        1 -> BrowserR.id.daxDialogOption2
                        2 -> BrowserR.id.daxDialogOption3
                        else -> BrowserR.id.daxDialogOption4 // This will not be visible for the experiments
                    }
                option.let { ctaView?.findViewById<MaterialButton>(optionView)?.setOnClickListener { onOptionClicked.invoke(option, index) } }
            }
        } else {
            options?.forEachIndexed { index, option ->
                val optionView =
                    when (index) {
                        0 -> BrowserR.id.daxDialogOption1
                        1 -> BrowserR.id.daxDialogOption2
                        2 -> BrowserR.id.daxDialogOption3
                        else -> BrowserR.id.daxDialogOption4
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
        title = BrowserR.string.onboardingSearchDaxDialogTitle,
        description = BrowserR.string.onboardingSearchDaxDialogDescription,
        options = onboardingStore.getSearchOptions(),
        shownPixel = AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        okPixel = AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        ctaPixelParam = Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore = onboardingStore,
        appInstallStore = appInstallStore,
    )

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

        companion object {
            private const val DIALOG_FADE_IN_DURATION = 400L
            private const val DIALOG_CONTENT_FADE_IN_DURATION = 200L
            private const val TYPING_DELAY_MS = 20L
            private const val TYPING_POST_DELAY_MS = 20L
            private const val DISMISS_BORDER_WIDTH_DP = 1.5f
        }

        abstract val activeIncludeId: Int

        abstract fun configureContentViews(view: View)

        protected fun resolveOnboardingContext(context: Context): Context {
            val themeRes = if (isLightTheme) {
                R.style.Theme_DuckDuckGo_Light_Onboarding
            } else {
                R.style.Theme_DuckDuckGo_Dark_Onboarding
            }
            return ContextThemeWrapper(context, themeRes)
        }

        private fun styleDismissButton(button: ImageView) {
            val themedContext = resolveOnboardingContext(button.context)
            val bgColor = themedContext.getColorFromAttr(R.attr.onboardingSurfaceTertiary)
            val borderColor = themedContext.getColorFromAttr(R.attr.onboardingAccentAltPrimary)
            val iconColor = themedContext.getColorFromAttr(R.attr.onboardingIconsPrimary)

            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(DISMISS_BORDER_WIDTH_DP.toPx().toInt(), borderColor)
            }
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(iconColor))
        }

        private fun getAllContentIncludes(view: View): List<View> = listOfNotNull(
            view.findViewById<View>(BrowserR.id.optionsContent),
            // Future: view.findViewById<View>(BrowserR.id.primaryCtaContent),
            // Future: view.findViewById<View>(BrowserR.id.dualButtonsContent),
        )

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

            var animationFinished = false
            var contentFadeInAnimator: AnimatorSet? = null
            val isContentTransition = container.alpha > 0f // card already visible from previous CTA

            val daxTitle = container.context.getString(title)
            val daxDescription = container.context.getString(description)

            val titleView = container.findViewById<DaxTypeAnimationTextView>(BrowserR.id.brandDesignTitle)
            val hiddenTitle = container.findViewById<DaxTextView>(BrowserR.id.brandDesignHiddenTitle)
            val descriptionView = container.findViewById<DaxTextView>(BrowserR.id.brandDesignDescription)
            val dismissButton = container.findViewById<ImageView>(BrowserR.id.brandDesignDismissButton)
            styleDismissButton(dismissButton)
            val cardContainer = container.findViewById<View>(BrowserR.id.brandDesignCardContainer)

            // The active content include for THIS CTA
            val activeInclude = container.findViewById<View>(activeIncludeId)

            // Helper: type title then fade in content
            val typeAndFadeIn = {
                hiddenTitle.text = daxTitle.html(container.context)
                descriptionView.text = daxDescription.html(container.context)
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
                    contentFadeInAnimator = AnimatorSet().apply {
                        playTogether(animators.toList())
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (!animationFinished) {
                                    animationFinished = true
                                    onTypingAnimationFinished()
                                }
                            }
                        })
                        start()
                    }
                }
            }

            if (isContentTransition) {
                // Content transition: fade out old description + any visible content include, then swap and animate new
                val allContentIncludes = getAllContentIncludes(container)
                val fadeOutAnimators = mutableListOf<Animator>(
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
                AnimatorSet().apply {
                    playTogether(fadeOutAnimators.toList())
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // After fade-out: hide old includes, show new one, type and fade in
                            // Note: do NOT call clearDialog() here — it would re-zero the dismiss
                            // button alpha causing a flicker. Instead, selectively reset content only.
                            resetAllIncludesExcept(container, activeInclude)
                            // Configure content views for this CTA
                            configureContentViews(container)
                            typeAndFadeIn()
                        }
                    })
                    start()
                }
            } else {
                clearDialog()
                resetAllIncludesExcept(container, activeInclude)
                hiddenTitle.text = daxTitle.html(container.context)
                descriptionView.text = daxDescription.html(container.context)
                configureContentViews(container)
                container.show()
                container.animate().alpha(1f).setDuration(DIALOG_FADE_IN_DURATION).setStartDelay(200L)
                    .withEndAction {
                        if (!animationFinished) {
                            typeAndFadeIn()
                        }
                    }
            }

            // Tap-to-skip: end running animations and snap all content visible
            fun snapToFinished() {
                titleView.finishAnimation()
                // If typing hasn't started yet (tap during initial fade-in), set title directly
                if (!titleView.hasAnimationStarted()) {
                    titleView.text = daxTitle.html(container.context)
                }
                descriptionView.alpha = 1f
                dismissButton.alpha = 1f
                activeInclude.alpha = 1f
                contentFadeInAnimator?.let { if (it.isRunning) it.end() }
                if (!animationFinished) {
                    animationFinished = true
                    onTypingAnimationFinished()
                }
            }
            cardContainer.setOnClickListener { snapToFinished() }
        }

        override fun clearDialog() {
            ctaView?.let { view ->
                view.findViewById<DaxTypeAnimationTextView>(BrowserR.id.brandDesignTitle)?.apply {
                    alpha = 1f
                    text = ""
                }
                view.findViewById<DaxTextView>(BrowserR.id.brandDesignDescription)?.alpha = 0f
                view.findViewById<View>(BrowserR.id.brandDesignDismissButton)?.alpha = 0f
                // Hide all content includes — include-level alpha/gone is sufficient;
                // children don't need individual alpha management since the parent
                // include's alpha controls their composite visibility.
                getAllContentIncludes(view).forEach { include ->
                    include.alpha = 0f
                    include.gone()
                }
            }
        }

        override fun setOnPrimaryCtaClicked(onButtonClicked: () -> Unit) {
            // No-op by default. Subclasses with a primary CTA button override this.
        }

        override fun setOnSecondaryCtaClicked(onButtonClicked: () -> Unit) {
            // No-op by default. Subclasses with a secondary CTA button override this.
        }

        override fun setOnDismissCtaClicked(onButtonClicked: () -> Unit) {
            ctaView?.findViewById<View>(BrowserR.id.brandDesignDismissButton)?.setOnClickListener {
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
        title = BrowserR.string.onboardingSitesDaxDialogTitle,
        description = BrowserR.string.onboardingSitesDaxDialogDescription,
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
        title = BrowserR.string.onboardingEndDaxDialogTitle,
        description = BrowserR.string.onboardingEndDaxDialogDescription,
        primaryCta = BrowserR.string.onboardingEndDaxDialogButton,
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
        title = BrowserR.string.onboardingPrivacyProDaxDialogTitle,
        description = BrowserR.string.onboardingPrivacyProDaxDialogDescription,
        placeholder = R.drawable.ic_privacy_pro_128,
        primaryCta = if (isFreeTrialCopy) {
            BrowserR.string.onboardingPrivacyProDaxDialogFreeTrialOkButton
        } else {
            BrowserR.string.onboardingPrivacyProDaxDialogOkButton
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
            BrowserR.drawable.add_widget_cta_icon,
            BrowserR.string.addWidgetCtaTitle,
            BrowserR.string.addWidgetCtaDescription,
            BrowserR.string.addWidgetCtaAutoLaunchButton,
            BrowserR.string.addWidgetCtaDismissButton,
            AppPixelName.WIDGET_CTA_SHOWN,
            AppPixelName.WIDGET_CTA_LAUNCHED,
            AppPixelName.WIDGET_CTA_DISMISSED,
        )

    data object AddWidgetInstructions : HomePanelCta(
        CtaId.ADD_WIDGET,
        BrowserR.drawable.add_widget_cta_icon,
        BrowserR.string.addWidgetCtaTitle,
        BrowserR.string.addWidgetCtaDescription,
        BrowserR.string.addWidgetCtaInstructionsLaunchButton,
        BrowserR.string.addWidgetCtaDismissButton,
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

class SubscriptionPromoModalCta(
    val isFreeTrialCopy: Boolean,
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

fun DaxCta.addCtaToHistory(newCta: String): String {
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

fun DaxCta.canSendShownPixel(): Boolean {
    val param =
        onboardingStore.onboardingDialogJourney
            ?.split("-")
            .orEmpty()
            .toMutableList()
    return !(param.isNotEmpty() && param.any { it.split(":").firstOrNull().orEmpty() == ctaPixelParam })
}

fun String.getStringForOmnibarPosition(position: OmnibarType): String =
    when (position) {
        OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> this
        OmnibarType.SINGLE_BOTTOM -> replace("☝", "\uD83D\uDC47")
    }

private fun View.fadeIn(duration: Duration = 500.milliseconds): ViewPropertyAnimator = animate().alpha(1f).setDuration(duration.inWholeMilliseconds)
