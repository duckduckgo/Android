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

import android.view.View
import androidx.annotation.AnyRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.install.daysInstalled
import com.duckduckgo.app.global.view.DaxDialog
import com.duckduckgo.app.global.view.hide
import com.duckduckgo.app.global.view.html
import com.duckduckgo.app.global.view.show
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import kotlinx.android.synthetic.main.include_cta_buttons.view.*
import kotlinx.android.synthetic.main.include_cta_content.view.*
import kotlinx.android.synthetic.main.include_dax_dialog.view.dialogText
import kotlinx.android.synthetic.main.include_dax_dialog.view.hiddenText
import kotlinx.android.synthetic.main.include_dax_dialog.view.primaryCta

interface Cta {
    val ctaId: CtaId
    val shownPixel: Pixel.PixelName?
    val okPixel: Pixel.PixelName?
    val cancelPixel: Pixel.PixelName?

    fun pixelShownParameters(): Map<String, String?>
    fun pixelCancelParameters(): Map<String, String?>
    fun pixelOkParameters(): Map<String, String?>
}

sealed class DaxDialogCta(
    override val ctaId: CtaId,
    @AnyRes open val description: Int,
    @StringRes open val okButton: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    var ctaPixelParam: String,
    val onboardingStore: OnboardingStore,
    val appInstallStore: AppInstallStore
) : Cta {

    open fun getDaxText(activity: FragmentActivity): String = activity.getString(description)

    fun apply(activity: FragmentActivity): DaxDialog = createDialog(activity)

    open fun createDialog(activity: FragmentActivity): DaxDialog = DaxDialog(getDaxText(activity), activity.resources.getString(okButton))

    override fun pixelCancelParameters(): Map<String, String?> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String?> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String?> {
        val param = onboardingStore.onboardingDialogJourney?.split("-")
            .orEmpty()
            .map { it }
            .toMutableList()
        param.add("$ctaPixelParam:${appInstallStore.daysInstalled().toInt()}")
        val finalParam = param.joinToString("-")
        onboardingStore.onboardingDialogJourney = finalParam

        return mapOf(
            Pixel.PixelParameter.CTA_SHOWN to finalParam
        )
    }

    class DaxSerpCta(onboarding: OnboardingStore, appStore: AppInstallStore) : DaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.daxSerpCtaText,
        R.string.daxDialogPhew,
        Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
        Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_SERP_CTA,
        onboarding,
        appStore
    )

    class DaxTrackersBlockedCta(onboarding: OnboardingStore, appStore: AppInstallStore, val trackers: List<TrackingEvent>, val host: String) :
        DaxDialogCta(
            CtaId.DAX_DIALOG_TRACKERS_FOUND,
            R.plurals.daxTrackersBlockedCtaText,
            R.string.daxDialogHighFive,
            Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
            Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
            null,
            Pixel.PixelValues.DAX_TRACKERS_BLOCKED_CTA,
            onboarding,
            appStore
        ) {
        override fun getDaxText(activity: FragmentActivity): String {
            val trackersFiltered = trackers.asSequence()
                .filter { it.trackerNetwork?.isMajor == true }
                .map { it.trackerNetwork?.name }
                .distinct()
                .take(MAX_TRACKERS_SHOWS)
                .toList()

            val trackersText = trackersFiltered.joinToString(", ")
            val size = trackers.size - trackersFiltered.size
            val quantityString =
                if (size == 0) {
                    activity.resources.getString(R.string.daxTrackersBlockedCtaZeroText, host.removePrefix("www."))
                } else {
                    activity.resources.getQuantityString(description, size, size, host.removePrefix("www."))
                }
            return "<b>$trackersText</b>$quantityString"
        }
    }

    class DaxMainNetworkCta(onboarding: OnboardingStore, appStore: AppInstallStore, val network: String, val host: String) : DaxDialogCta(
        CtaId.DAX_DIALOG_NETWORK,
        R.string.daxMainNetworkStep1CtaText,
        R.string.daxDialogNext,
        Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
        Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NETWORK_CTA_1,
        onboarding,
        appStore
    ) {
        fun getNetworkName(): String? = MAIN_TRACKER_NETWORKS_NAMES[network]

        fun firstParagraph(activity: FragmentActivity): String {
            val percentage = NETWORK_PROPERTY_PERCENTAGES[network]
            return if (percentage != null)
                activity.resources.getString(R.string.daxMainNetworkStep21CtaText, getNetworkName(), percentage)
            else activity.resources.getString(R.string.daxMainNetworkStep211CtaText, getNetworkName())
        }

        override fun getDaxText(activity: FragmentActivity): String {
            val isFromSameNetworkDomain = MAIN_TRACKER_DOMAINS.any { host.contains(it) }

            return if (isFromSameNetworkDomain) {
                activity.resources.getString(description, "This website", getNetworkName())
            } else {
                activity.resources.getString(description, host.removePrefix("www."), getNetworkName())
            }
        }

        override fun createDialog(activity: FragmentActivity): DaxDialog {
            return DaxDialog(getDaxText(activity), activity.resources.getString(okButton))
        }
    }

    class DaxNoSerpCta(onboarding: OnboardingStore, appStore: AppInstallStore) : DaxDialogCta(
        CtaId.DAX_DIALOG_OTHER,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
        Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_NO_TRACKERS_CTA,
        onboarding,
        appStore
    ) {

        override fun createDialog(activity: FragmentActivity): DaxDialog {
            return DaxDialog(getDaxText(activity), activity.resources.getString(okButton)).apply {
                val fireButton = activity.findViewById<View>(R.id.fire)
                onAnimationFinishedListener {
                    startHighlightViewAnimation(fireButton)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DaxDialog"
        private const val MAX_TRACKERS_SHOWS = 2
        const val SERP = "duckduckgo"
        val MAIN_TRACKER_DOMAINS = listOf("facebook", "google")
        val MAIN_TRACKER_NETWORKS = listOf("Facebook", "Google")
        val MAIN_TRACKER_NETWORKS_NAMES =
            mapOf(Pair("Facebook", "Facebook"), Pair("Google", "Google"))
        val NETWORK_PROPERTY_PERCENTAGES = mapOf(Pair("Google", "90%"), Pair("Facebook", "40%"))
    }
}

sealed class DaxBubbleCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?,
    private val ctaPixelParam: String,
    val onboardingStore: OnboardingStore,
    val appInstallStore: AppInstallStore
) : Cta {

    var afterAnimation: () -> Unit = {}

    override fun pixelCancelParameters(): Map<String, String?> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelOkParameters(): Map<String, String?> = mapOf(Pixel.PixelParameter.CTA_SHOWN to ctaPixelParam)

    override fun pixelShownParameters(): Map<String, String?> {
        val param = onboardingStore.onboardingDialogJourney?.split("-")
            .orEmpty()
            .map { it }
            .toMutableList()
        param.add("$ctaPixelParam:${appInstallStore.daysInstalled().toInt()}")
        val finalParam = param.joinToString("-")
        onboardingStore.onboardingDialogJourney = finalParam

        return mapOf(
            Pixel.PixelParameter.CTA_SHOWN to finalParam
        )
    }

    class DaxIntroCta(onboarding: OnboardingStore, appStore: AppInstallStore) : DaxBubbleCta(
        CtaId.DAX_INTRO,
        R.string.daxIntroCtaText,
        Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
        Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_INITIAL_CTA,
        onboarding,
        appStore
    )

    class DaxEndCta(onboarding: OnboardingStore, appStore: AppInstallStore) : DaxBubbleCta(
        CtaId.DAX_END,
        R.string.daxEndCtaText,
        Pixel.PixelName.ONBOARDING_DAX_CTA_SHOWN,
        Pixel.PixelName.ONBOARDING_DAX_CTA_OK_BUTTON,
        null,
        Pixel.PixelValues.DAX_END_CTA,
        onboarding,
        appStore
    )

    fun apply(view: View) {
        val daxText = view.context.getString(description)
        view.show()
        view.alpha = 1f
        view.hiddenText.text = daxText.html(view.context)
        view.primaryCta.hide()
        view.dialogText.startTypingAnimation(daxText, true, afterAnimation = afterAnimation)
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
    override val cancelPixel: Pixel.PixelName?
) : Cta {

    fun apply(view: View) {
        view.ctaIcon.setImageResource(image)
        view.ctaTitle.text = view.context.getString(title)
        view.ctaSubtitle.text = view.context.getString(description)
        view.ctaOkButton.text = view.context.getString(okButton)
        view.ctaDismissButton.text = view.context.getString(dismissButton)
    }

    override fun pixelCancelParameters(): Map<String, String?> = emptyMap()

    override fun pixelOkParameters(): Map<String, String?> = emptyMap()

    override fun pixelShownParameters(): Map<String, String?> = emptyMap()

    data class Survey(val survey: com.duckduckgo.app.survey.model.Survey) : HomePanelCta(
        CtaId.SURVEY,
        R.drawable.survey_cta_icon,
        R.string.surveyCtaTitle,
        R.string.surveyCtaDescription,
        R.string.surveyCtaLaunchButton,
        R.string.surveyCtaDismissButton,
        Pixel.PixelName.SURVEY_CTA_SHOWN,
        Pixel.PixelName.SURVEY_CTA_LAUNCHED,
        Pixel.PixelName.SURVEY_CTA_DISMISSED
    )

    object AddWidgetAuto : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaAutoLaunchButton,
        R.string.addWidgetCtaDismissButton,
        null,
        null,
        null
    )

    object AddWidgetInstructions : HomePanelCta(
        CtaId.ADD_WIDGET,
        R.drawable.add_widget_cta_icon,
        R.string.addWidgetCtaTitle,
        R.string.addWidgetCtaDescription,
        R.string.addWidgetCtaInstructionsLaunchButton,
        R.string.addWidgetCtaDismissButton,
        null,
        null,
        null
    )
}