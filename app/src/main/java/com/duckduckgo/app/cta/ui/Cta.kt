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
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.ViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeExperimentViewDaxDialogBinding
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.ui.DaxCta.Companion.MAX_DAYS_ALLOWED
import com.duckduckgo.app.cta.ui.ExperimentDaxBubbleOptionsCta.DaxDialogIntroOption
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DAX_FIRE_DIALOG_CTA
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.extensions.html

interface ViewCta {
    fun showCta(view: View)
}

interface DaxCta {
    val onboardingStore: OnboardingStore
    val appInstallStore: AppInstallStore
    var ctaPixelParam: String

    companion object {
        const val MAX_DAYS_ALLOWED = 3
    }
}

interface Cta {
    val ctaId: CtaId
    val shownPixel: Pixel.PixelName?
    val okPixel: Pixel.PixelName?
    val cancelPixel: Pixel.PixelName?

    fun pixelShownParameters(): Map<String, String>
    fun pixelCancelParameters(): Map<String, String>
    fun pixelOkParameters(): Map<String, String>
}

interface OnboardingDaxCta {
    fun showOnboardingCta(
        binding: FragmentBrowserTabBinding,
        onPrimaryCtaClicked: () -> Unit,
        onTypingAnimationFinished: () -> Unit,
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
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, DaxCta, OnboardingDaxCta {

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
    ) : OnboardingDaxDialogCta(
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
    ) : OnboardingDaxDialogCta(
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
    ) : OnboardingDaxDialogCta(
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
    ) : OnboardingDaxDialogCta(
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
    ) : OnboardingDaxDialogCta(
        CtaId.DAX_FIRE_BUTTON,
        R.string.onboardingFireButtonDaxDialogDescription,
        null,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        DAX_FIRE_DIALOG_CTA,
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
    ) : OnboardingDaxDialogCta(
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
    @StringRes open val description: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    override var ctaPixelParam: String,
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
) : Cta, ViewCta, DaxCta {

    override fun showCta(view: View) {
        val daxText = view.context.getString(description)
        view.show()
        view.alpha = 1f
        view.findViewById<TextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.findViewById<View>(R.id.primaryCta).hide()
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true)
    }

    override fun pixelCancelParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String> = mapOf(Pixel.PixelParameter.CTA_SHOWN to addCtaToHistory(ctaPixelParam))

    class DaxIntroCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        CtaId.DAX_INTRO,
        R.string.daxIntroCtaText,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_CTA,
        onboardingStore,
        appInstallStore,
    )

    class DaxEndCta(
        override val onboardingStore: OnboardingStore,
        override val appInstallStore: AppInstallStore,
    ) : DaxBubbleCta(
        CtaId.DAX_END,
        R.string.daxEndCtaText,
        AppPixelName.ONBOARDING_DAX_CTA_SHOWN,
        AppPixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_END_CTA,
        onboardingStore,
        appInstallStore,
    )
}

sealed class BubbleCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
) : Cta, ViewCta {

    override fun showCta(view: View) {
        val daxText = view.context.getString(description)
        view.findViewById<View>(R.id.primaryCta).hide()
        view.findViewById<TextView>(R.id.hiddenTextCta).text = daxText.html(view.context)
        view.show()
        view.alpha = 1f
        view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).startTypingAnimation(daxText, true)
    }

    override fun pixelCancelParameters(): Map<String, String> = emptyMap()

    override fun pixelOkParameters(): Map<String, String> = emptyMap()

    override fun pixelShownParameters(): Map<String, String> = emptyMap()

    class DaxFavoritesOnboardingCta : BubbleCta(
        CtaId.DAX_FAVORITES_ONBOARDING,
        R.string.daxFavoritesOnboardingCtaText,
        AppPixelName.FAVORITES_ONBOARDING_CTA_SHOWN,
        null,
        null,
    ) {
        override fun showCta(view: View) {
            super.showCta(view)
            val accessibilityDelegate: View.AccessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.text = host.context?.getString(R.string.daxFavoritesOnboardingCtaContentDescription)
                    }
                }
            // Using braille unicode inside textview (to simulate the overflow icon), override description for accessibility
            view.findViewById<TypeAnimationTextView>(R.id.dialogTextCta).accessibilityDelegate = accessibilityDelegate
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
) : Cta, ViewCta {

    override fun showCta(view: View) {
        view.findViewById<ImageView>(R.id.ctaIcon).setImageResource(image)
        view.findViewById<TextView>(R.id.ctaTitle).text = view.context.getString(title)
        view.findViewById<TextView>(R.id.ctaSubtitle).text = view.context.getString(description)
        view.findViewById<Button>(R.id.ctaOkButton).text = view.context.getString(okButton)
        view.findViewById<Button>(R.id.ctaDismissButton).text = view.context.getString(dismissButton)
        view.show()
    }

    override fun pixelCancelParameters(): Map<String, String> = emptyMap()

    override fun pixelOkParameters(): Map<String, String> = emptyMap()

    override fun pixelShownParameters(): Map<String, String> = emptyMap()

    data class Survey(val survey: com.duckduckgo.app.survey.model.Survey) : HomePanelCta(
        CtaId.SURVEY,
        R.drawable.survey_cta_icon,
        R.string.surveyCtaTitle,
        R.string.surveyCtaDescription,
        R.string.surveyCtaLaunchButton,
        R.string.surveyCtaDismissButton,
        AppPixelName.SURVEY_CTA_SHOWN,
        AppPixelName.SURVEY_CTA_LAUNCHED,
        AppPixelName.SURVEY_CTA_DISMISSED,
    )

    object DeviceShieldCta : HomePanelCta(
        CtaId.DEVICE_SHIELD_CTA,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaAutoLaunchButton,
        R.string.addWidgetCtaDismissButton,
        null,
        null,
        null,
    )

    object AddWidgetAuto : HomePanelCta(
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
