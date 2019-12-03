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
import com.duckduckgo.app.global.view.DaxDialog
import com.duckduckgo.app.global.view.hide
import com.duckduckgo.app.global.view.html
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
    fun apply(view: View?, activity: FragmentActivity?)
}

sealed class DaxDialogCta(
    override val ctaId: CtaId,
    @AnyRes open val description: Int,
    @StringRes open val okButton: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?
) : Cta {

    open fun getDaxText(activity: FragmentActivity): String = activity.getString(description)

    fun createDialog(activity: FragmentActivity): DaxDialog =
        DaxDialog(getDaxText(activity), activity.resources.getString(okButton))

    object DaxSerpCta : DaxDialogCta(
        CtaId.DAX_DIALOG_SERP,
        R.string.daxSerpCtaText,
        R.string.daxDialogPhew,
        null,
        null,
        null
    ) {
        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                createDialog(activity).show(it.supportFragmentManager, TAG)
            }
        }
    }

    class DaxTrackersBlockedCta(val trackers: List<TrackingEvent>, val host: String) : DaxDialogCta(
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        R.plurals.daxTrackersBlockedCtaText,
        R.string.daxDialogHighFive,
        null,
        null,
        null
    ) {
        override fun getDaxText(activity: FragmentActivity): String {
            val trackersFiltered = trackers.asSequence()
                .filter { MAIN_TRACKER_NETWORKS.contains(it.trackerNetwork?.name) }
                .map { MAIN_TRACKER_NETWORKS_NAMES[it.trackerNetwork?.name] }
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

        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                createDialog(activity).show(it.supportFragmentManager, TAG)
            }
        }
    }

    class DaxMainNetworkCta(val network: String, val host: String) : DaxDialogCta(
        CtaId.DAX_DIALOG_NETWORK,
        R.string.daxMainNetworkStep1CtaText,
        R.string.daxDialogNext,
        null,
        null,
        null
    ) {
        private fun getNetworkName(): String? = MAIN_TRACKER_NETWORKS_NAMES[network]

        private fun firstParagraph(activity: FragmentActivity): String {
            val percentage = NETWORK_PROPERTY_PERCENTAGES[network]
            return if (percentage != null)
                activity.resources.getString(R.string.daxMainNetworkStep21CtaText, getNetworkName(), percentage)
            else ""
        }

        override fun getDaxText(activity: FragmentActivity): String = activity.resources.getString(description, host.removePrefix("www."), getNetworkName())

        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                createDialog(activity).apply {
                    setPrimaryCTAClickListener {
                        val firstParagraph = firstParagraph(it)
                        daxText = it.resources.getString(R.string.daxMainNetworkStep2CtaText, firstParagraph, getNetworkName())
                        buttonText = it.resources.getString(R.string.daxDialogGotIt)
                        setPrimaryCTAClickListener { dismiss() }
                        setDialogAndStartAnimation()
                    }
                    show(it.supportFragmentManager, TAG)
                }
            }
        }
    }

    object DaxNoSerpCta : DaxDialogCta(
        CtaId.DAX_DIALOG_OTHER,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        null,
        null,
        null
    ) {
        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                createDialog(activity).apply {
                    val fireButton = activity.findViewById<View>(R.id.fire)
                    onAnimationFinishedListener {
                        startHighlightViewAnimation(fireButton)
                    }
                    show(it.supportFragmentManager, TAG)
                }
            }
        }
    }

    companion object {
        private const val TAG = "DaxDialog"
        private const val MAX_TRACKERS_SHOWS = 2
        const val SERP = "duckduckgo"
        val MAIN_TRACKER_NETWORKS = listOf("Facebook", "Amazon.com", "Twitter", "Google")
        val MAIN_TRACKER_NETWORKS_NAMES =
            mapOf(Pair("Facebook", "Facebook"), Pair("Amazon.com", "Amazon"), Pair("Twitter", "Twitter"), Pair("Google", "Google"))
        val NETWORK_PROPERTY_PERCENTAGES = mapOf(Pair("Google", "90%"), Pair("Facebook", "40%"))
    }
}

sealed class DaxBubbleCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?
) : Cta {

    var afterAnimation: () -> Unit = {}

    object DaxIntroCta : DaxBubbleCta(
        CtaId.DAX_INTRO,
        R.string.daxIntroCtaText,
        null,
        null,
        null
    )

    object DaxEndCta : DaxBubbleCta(
        CtaId.DAX_END,
        R.string.daxEndCtaText,
        null,
        null,
        null
    )

    override fun apply(view: View?, activity: FragmentActivity?) {
        view?.let {
            val daxText = view.context.getString(description)
            view.alpha = 1f
            view.hiddenText.text = daxText.html(view.context)
            view.primaryCta.hide()
            view.dialogText.startTypingAnimation(daxText, true, afterAnimation = afterAnimation)
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
    override val cancelPixel: Pixel.PixelName?
) : Cta {

    override fun apply(view: View?, activity: FragmentActivity?) {
        view?.let {
            view.ctaIcon.setImageResource(image)
            view.ctaTitle.text = view.context.getString(title)
            view.ctaSubtitle.text = view.context.getString(description)
            view.ctaOkButton.text = view.context.getString(okButton)
            view.ctaDismissButton.text = view.context.getString(dismissButton)
        }
    }

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