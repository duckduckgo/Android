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

    object DaxSerpCta : DaxDialogCta(
        CtaId.DAX_DIALOG,
        R.string.daxSerpCtaText,
        R.string.daxDialogPhew,
        null,
        null,
        null
    ) {
        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                val daxText = getDaxText(activity)
                val dialog = DaxDialog(daxText, activity.resources.getString(okButton))
                dialog.show(it.supportFragmentManager, "test")
            }
        }
    }

    class DaxTrackersBlockedCta(val trackers: List<TrackingEvent>) : DaxDialogCta(
        CtaId.DAX_DIALOG,
        R.plurals.daxTrackersBlockedCtaText,
        R.string.daxDialogHighFive,
        null,
        null,
        null
    ) {
        override fun getDaxText(activity: FragmentActivity): String {
            val trackersFiltered =
                trackers.asSequence().filter { MAIN_TRACKER_NETWORKS.contains(it.trackerNetwork?.name) }
                    .map { MAIN_TRACKER_NETWORKS_NAMES[it.trackerNetwork?.name] }.take(2)
                    .toList()
            val trackersText = trackersFiltered.joinToString(",")
            val size = trackers.size - trackersFiltered.size
            return "<b>$trackersText</b>" + activity.resources.getQuantityString(description, size, size)
        }

        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                val daxText = getDaxText(activity)
                val dialog = DaxDialog(daxText, activity.resources.getString(okButton))
                dialog.show(it.supportFragmentManager, "test")
            }
        }
    }

    class DaxMainNetworkCta(val network: String) : DaxDialogCta(
        CtaId.DAX_DIALOG,
        R.string.daxMainNetworkStep1CtaText,
        R.string.daxDialogNext,
        null,
        null,
        null
    ) {
        private fun getNetworkName(): String? = MAIN_TRACKER_NETWORKS_NAMES[network]

        override fun getDaxText(activity: FragmentActivity): String = activity.resources.getString(description, getNetworkName(), getNetworkName())

        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                val daxText = getDaxText(activity)
                val dialog = DaxDialog(daxText, activity.resources.getString(okButton))
                dialog.setPrimaryCTAClickListener {
                    val percentage = NETWORK_PROPERTY_PERCENTAGES[network]
                    val firstParagraph =
                        if (percentage != null)
                            activity.resources.getString(R.string.daxMainNetworkStep21CtaText, getNetworkName(), percentage)
                        else ""
                    dialog.daxText =
                        activity.resources.getString(R.string.daxMainNetworkStep2CtaText, firstParagraph, getNetworkName(), getNetworkName())
                    dialog.buttonText = activity.resources.getString(R.string.daxDialogGotIt)
                    dialog.setPrimaryCTAClickListener { dialog.dismiss() }
                    dialog.onStart()
                }
                dialog.show(it.supportFragmentManager, "test")
            }
        }
    }

    object DaxNoSerpCta : DaxDialogCta(
        CtaId.DAX_DIALOG,
        R.string.daxNonSerpCtaText,
        R.string.daxDialogGotIt,
        null,
        null,
        null
    ) {
        override fun apply(view: View?, activity: FragmentActivity?) {
            activity?.let {
                val daxText = getDaxText(activity)
                val dialog = DaxDialog(daxText, activity.resources.getString(okButton))
                val fireButton = activity.findViewById<View>(R.id.fire)
                dialog.onAnimationFinishedListener {
                    dialog.startHighlightViewAnimation(fireButton)
                }
                dialog.show(it.supportFragmentManager, "test")
            }
        }
    }

    companion object {
        val MAIN_TRACKER_NETWORKS = listOf("Facebook", "Amazon.com", "Twitter", "Google")
        val MAIN_TRACKER_NETWORKS_NAMES =
            mapOf(Pair("Facebook", "Facebook"), Pair("Amazon.com", "Amazon"), Pair("Twitter", "Twitter"), Pair("Google", "Google"))
        val NETWORK_PROPERTY_PERCENTAGES = mapOf(Pair("Google", "90%"), Pair("Facebook", "40%"))
        val SERP = "duckduckgo"
    }
}

sealed class DaxBubbleCta(
    override val ctaId: CtaId,
    @StringRes open val description: Int,
    override val shownPixel: Pixel.PixelName?,
    override val okPixel: Pixel.PixelName?,
    override val cancelPixel: Pixel.PixelName?
) : Cta {

    object DaxIntroCta : DaxBubbleCta(
        CtaId.DAX_DIALOG,
        R.string.daxIntroCtaText,
        null,
        null,
        null
    )

    override fun apply(view: View?, activity: FragmentActivity?) {
        view?.let {
            val daxText = view.context.getString(description)
            view.alpha = 1f
            view.hiddenText.text = daxText
            view.primaryCta.hide()
            view.dialogText.startTypingAnimation(daxText, true)
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