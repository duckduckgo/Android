/*
 * Copyright (c) 2026 DuckDuckGo
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
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.TextViewCompat
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.utils.device.DeviceInfo
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt
import com.duckduckgo.mobile.android.R as DesignSystemR

/**
 * Suggests Sync on the new tab page of a phone set up by a device transfer.
 *
 * It borrows the onboarding bubble so it looks like the onboarding dialogs, but it is not part of onboarding: it sends
 * no onboarding pixels, and whether it shows is decided by Sync, not by the dismissed-CTA table.
 */
data class SyncAfterDeviceTransferBubbleCta(
    override val onboardingStore: OnboardingStore,
    override val appInstallStore: AppInstallStore,
    override val isLightTheme: Boolean,
    override val deviceInfo: DeviceInfo,
) : DaxBubbleCta(
    ctaId = CtaId.SYNC_AFTER_DEVICE_TRANSFER,
    title = R.string.syncAfterDeviceTransferDaxDialogTitle,
    description = R.string.syncAfterDeviceTransferDaxDialogDescription,
    backgroundRes = R.drawable.bg_onboarding_subscription,
    shownPixel = null,
    okPixel = null,
    closePixel = null,
    ctaPixelParam = "",
    onboardingStore = onboardingStore,
    appInstallStore = appInstallStore,
    isLightTheme = isLightTheme,
    deviceInfo = deviceInfo,
),
    DaxBubbleCta.ShowsWavingDax {
    override val markAsReadOnShow: Boolean = false
    override val backgroundFillSpec = BackgroundFillSpec(fillHeightDp = 190f, tabletFillHeightDp = 280f, maxHeightFraction = 0.4f)
    override val activeIncludeIds: List<Int> = listOf(R.id.primaryCta)
    override val showArrow: Boolean = true
    override val restartWavingDax: Boolean = true

    override val wavingDaxSpec = WavingDaxSpec(
        rotationDegrees = 22.62f,
        translationXDp = -114f,
        translationYDp = -288f,
        minHeightDp = 178f,
        maxHeightDp = 267f,
        anchorToCardOnTablet = false,
    )

    override fun configureContentViews(view: View) {
        view.findViewById<ImageView>(R.id.brandDesignHeaderImage)?.apply {
            setImageResource(R.drawable.sync_feature_96)
            isVisible = true
        }
        val titleView = view.findViewById<TextView>(R.id.brandDesignTitle)
        listOfNotNull(titleView, view.findViewById(R.id.brandDesignHiddenTitle)).forEach {
            it.applyTypography(DesignSystemR.style.Typography_DuckDuckGo_Onboarding_Title, TITLE_LINE_HEIGHT_SP)
            it.gravity = Gravity.CENTER
        }
        (titleView?.parent as? View)?.let { it.setHorizontalMargins(it.dp(TEXT_SIDE_INSET_DP)) }
        view.findViewById<TextView>(R.id.brandDesignDescription)?.apply {
            applyTypography(DesignSystemR.style.Typography_DuckDuckGo_Onboarding_Body, BODY_LINE_HEIGHT_SP)
            gravity = Gravity.CENTER
            setHorizontalMargins(dp(TEXT_SIDE_INSET_DP))
            updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = dp(TITLE_TO_BODY_GAP_DP) }
        }
        view.findViewById<View>(R.id.brandDesignCardContainer)?.apply {
            updatePaddingRelative(top = dp(BUBBLE_TOP_PADDING_DP))
        }
        view.findViewById<MaterialButton>(R.id.primaryCta)?.apply {
            setText(R.string.syncAfterDeviceTransferDaxDialogPrimaryCta)
            updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = dp(BODY_TO_BUTTON_GAP_DP) }
        }
        view.findViewById<View>(R.id.brandDesignDismissButton)?.apply {
            val offset = dp(DISMISS_OFFSET_DP)
            updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = offset
                marginEnd = offset
            }
        }
    }

    /** The two paragraphs are split by a blank line, which would leave a full line of space between them. */
    public override fun decorateDescription(
        context: Context,
        text: CharSequence,
    ): CharSequence {
        val paragraphBreak = text.indexOf(PARAGRAPH_BREAK)
        if (paragraphBreak < 0) return text
        val blankLineStart = paragraphBreak + 1
        return SpannableStringBuilder(text).apply {
            setSpan(
                ExactLineHeightSpan((PARAGRAPH_GAP_DP * context.resources.displayMetrics.density).roundToInt()),
                blankLineStart,
                blankLineStart + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun TextView.applyTypography(
        @StyleRes style: Int,
        lineHeightSp: Float,
    ) {
        TextViewCompat.setTextAppearance(this, style)
        TextViewCompat.setLineHeight(this, TypedValue.COMPLEX_UNIT_SP, lineHeightSp)
    }

    private fun View.dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun View.setHorizontalMargins(margin: Int) {
        updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = margin
            marginEnd = margin
        }
    }

    internal class ExactLineHeightSpan(private val heightPx: Int) : LineHeightSpan {
        override fun chooseHeight(
            text: CharSequence,
            start: Int,
            end: Int,
            spanstartv: Int,
            lineHeight: Int,
            fm: Paint.FontMetricsInt,
        ) {
            fm.ascent = fm.descent - heightPx
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
    }

    private companion object {
        const val PARAGRAPH_BREAK = "\n\n"
        const val TITLE_LINE_HEIGHT_SP = 28f
        const val BODY_LINE_HEIGHT_SP = 22f
        const val BUBBLE_TOP_PADDING_DP = 20f
        const val TEXT_SIDE_INSET_DP = 32f
        const val TITLE_TO_BODY_GAP_DP = 8f
        const val PARAGRAPH_GAP_DP = 10f
        const val BODY_TO_BUTTON_GAP_DP = 27f
        const val DISMISS_OFFSET_DP = -5.5f
    }

    override fun shouldDropAddressBarFocusWhenShown(): Boolean = true
}
