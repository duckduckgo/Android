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

package com.duckduckgo.app.dev.settings.quiz

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.duckduckgo.common.ui.compose.DaxStatusIndicator
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.panel.DaxInfoPanel
import com.duckduckgo.common.ui.compose.progress.DaxProgressSpinner
import com.duckduckgo.common.ui.compose.skeleton.DaxSkeletonListItem
import com.duckduckgo.common.ui.compose.skeleton.DaxSkeletonSectionHeader
import com.duckduckgo.common.ui.view.SkeletonView
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.mobile.android.R
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.duckduckgo.app.browser.R as AppR

internal val statusQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.StatusIndicator to quizRenderer(
        createView = ::statusIndicatorSample,
        composeSample = {
            Column {
                DaxStatusIndicator(Status.AlwaysOn)
                DaxStatusIndicator(Status.On)
                DaxStatusIndicator(Status.Off)
            }
        },
    ),
    QuestionId.InfoPanel to quizRenderer(
        createView = { context -> LayoutInflater.from(context).inflate(AppR.layout.quiz_view_info_panel, null, false) },
        composeSample = { DaxInfoPanel(body = INFO_PANEL_TEXT, modifier = Modifier.fillMaxWidth()) },
    ),
    QuestionId.ProgressSpinner to quizRenderer(
        createView = { context ->
            val spinner = CircularProgressIndicator(context).apply {
                isIndeterminate = true
                val color = context.getColorFromAttr(R.attr.daxColorPrimaryText)
                setIndicatorColor(ColorUtils.setAlphaComponent(color, SPINNER_INDICATOR_ALPHA))
                trackColor = ColorUtils.setAlphaComponent(color, SPINNER_TRACK_ALPHA)
            }
            startAligned(spinner, margin = -spinner.indicatorInset)
        },
        composeSample = { DaxProgressSpinner() },
    ),
    QuestionId.SkeletonListHeader to quizRenderer(
        createView = ::skeletonListHeaderSample,
        composeSample = { SkeletonListHeaderSample() },
    ),
)

// Matches the 54% head and 18% track alphas DaxProgressSpinner takes from the theme.
private const val SPINNER_INDICATOR_ALPHA = 138
private const val SPINNER_TRACK_ALPHA = 46
private const val INFO_PANEL_TEXT = "This is an info panel. It can be used to show important information to the user."

private fun statusIndicatorSample(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    addView(StatusIndicatorView(context).apply { setStatus(StatusIndicatorView.Status.ALWAYS_ON) })
    addView(StatusIndicatorView(context).apply { setStatus(StatusIndicatorView.Status.ON) })
    addView(StatusIndicatorView(context).apply { setStatus(StatusIndicatorView.Status.OFF) })
}

private fun skeletonListHeaderSample(context: Context): ShimmerFrameLayout = ShimmerFrameLayout(context).apply {
    startShimmer()
    addView(skeletonListHeaderContent(context))
}

private fun skeletonListHeaderContent(context: Context): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    addView(
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(context.dpToPixels(16), 0, 0, 0)
            addView(
                skeletonAvatar(context),
                fixedSizeLayoutParams(context, SKELETON_CIRCLE_SIZE_DP, SKELETON_CIRCLE_SIZE_DP),
            )
            addView(
                SkeletonView(context),
                fixedSizeLayoutParams(context, SKELETON_LIST_LINE_WIDTH_DP, SKELETON_LINE_HEIGHT_DP).apply {
                    marginStart = context.dpToPixels(SKELETON_ITEM_GAP_DP)
                },
            )
            addView(Space(context), fixedSizeLayoutParams(context, SKELETON_TRAILING_SPACER_DP, 0))
        },
        fixedSizeLayoutParams(context, SKELETON_LIST_WIDTH_DP, ViewGroup.LayoutParams.WRAP_CONTENT),
    )
    addView(
        LinearLayout(context).apply {
            setPadding(
                context.dpToPixels(SKELETON_HEADER_HORIZONTAL_PADDING_DP),
                context.dpToPixels(SKELETON_HEADER_VERTICAL_PADDING_DP),
                context.dpToPixels(SKELETON_HEADER_HORIZONTAL_PADDING_DP),
                context.dpToPixels(SKELETON_HEADER_VERTICAL_PADDING_DP),
            )
            addView(SkeletonView(context), fixedSizeLayoutParams(context, SKELETON_HEADER_WIDTH_DP, SKELETON_LINE_HEIGHT_DP))
        },
        fixedSizeLayoutParams(context, SKELETON_HEADER_CONTAINER_WIDTH_DP, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dpToPixels(SKELETON_HEADER_MARGIN_TOP_DP)
        },
    )
}

@Composable
private fun SkeletonListHeaderSample() {
    Column {
        DaxSkeletonListItem(
            modifier = Modifier.width(SKELETON_LIST_WIDTH_DP.dp),
            hasLeadingIcon = true,
            hasTwoLines = false,
        )
        DaxSkeletonSectionHeader(
            modifier = Modifier
                .padding(top = SKELETON_HEADER_MARGIN_TOP_DP.dp)
                .width(SKELETON_HEADER_CONTAINER_WIDTH_DP.dp),
            hasTrailingIcon = false,
        )
    }
}

private fun fixedSizeLayoutParams(context: Context, widthDp: Int, heightDp: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
    context.dpToPixels(widthDp),
    context.dpToPixels(heightDp),
)

private fun Context.dpToPixels(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

private fun skeletonAvatar(context: Context): SkeletonView = SkeletonView(context).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(context.getColorFromAttr(R.attr.daxColorLines))
    }
}

private const val SKELETON_CIRCLE_SIZE_DP = 40
private const val SKELETON_LINE_HEIGHT_DP = 16
private const val SKELETON_ITEM_GAP_DP = 16
private const val SKELETON_LIST_LINE_WIDTH_DP = 120
private const val SKELETON_TRAILING_SPACER_DP = 64
private const val SKELETON_LIST_WIDTH_DP = 256
private const val SKELETON_HEADER_WIDTH_DP = 96
private const val SKELETON_HEADER_CONTAINER_WIDTH_DP = 320
private const val SKELETON_HEADER_HORIZONTAL_PADDING_DP = 16
private const val SKELETON_HEADER_VERTICAL_PADDING_DP = 14
private const val SKELETON_HEADER_MARGIN_TOP_DP = 16
