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

package com.duckduckgo.app.onboarding.ui.page

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.R as CommonR

data class ComparisonChartConfig(
    @StringRes val titleRes: Int,
    @StringRes val primaryCtaTextRes: Int,
    @DrawableRes val headerLeftIconRes: Int,
    val headerLeftIconSizeDp: Float,
    @StringRes val headerLeftLabelRes: Int?,
    val rows: List<Row>,
) {
    data class Row(
        @DrawableRes val iconRes: Int,
        @StringRes val textRes: Int,
    )

    companion object {
        val Default = ComparisonChartConfig(
            titleRes = R.string.preOnboardingDaxDialog2Title,
            primaryCtaTextRes = R.string.preOnboardingDaxDialog2Button,
            headerLeftIconRes = CommonR.drawable.ic_chrome,
            headerLeftIconSizeDp = 32f, // the target size is 31.5dp but the icon already has some padding
            headerLeftLabelRes = null,
            rows = listOf(
                Row(CommonR.drawable.ic_vpn_color_24_rebrand, R.string.preOnboardingComparisonChartItem1),
                Row(CommonR.drawable.ic_duck_ai_color_24_rebrand, R.string.preOnboardingComparisonChartDuckAi),
                Row(CommonR.drawable.ic_shield_color_24_rebrand, R.string.preOnboardingComparisonChartItem2),
                Row(CommonR.drawable.ic_cookies_color_24_rebrand, R.string.preOnboardingComparisonChartItem3),
                Row(CommonR.drawable.ic_profile_blocker_color_24_rebrand, R.string.preOnboardingComparisonChartItem4),
            ),
        )

        val Ai = ComparisonChartConfig(
            titleRes = R.string.preOnboardingDaxDialogAiTitle,
            primaryCtaTextRes = R.string.preOnboardingAiComparisonChartButton,
            headerLeftIconRes = CommonR.drawable.ic_ai_general_16,
            headerLeftIconSizeDp = 18f,
            headerLeftLabelRes = R.string.preOnboardingAiComparisonChartPopularAis,
            rows = listOf(
                Row(CommonR.drawable.ic_shield_color_24_rebrand, R.string.preOnboardingAiComparisonChartItem1),
                Row(CommonR.drawable.ic_duck_ai_color_24_rebrand, R.string.preOnboardingAiComparisonChartItem2),
                Row(CommonR.drawable.ic_lock_color_24_rebrand, R.string.preOnboardingAiComparisonChartItem3),
                Row(CommonR.drawable.ic_ai_general_color_24_rebrand, R.string.preOnboardingAiComparisonChartItem4),
            ),
        )
    }
}
