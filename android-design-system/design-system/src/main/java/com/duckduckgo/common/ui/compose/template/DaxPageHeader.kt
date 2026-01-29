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

package com.duckduckgo.common.ui.compose.template

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.StatusIndicator
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Dax Page Header component to be used in Dax related settings screens.
 *
 * @param title The title text to be displayed in the header.
 * @param modifier The [Modifier] to be applied to this header.
 * @param subtitle An optional subtitle text to be displayed below the title.
 * @param body An optional body text to be displayed below the subtitle.
 * @param iconHeader An optional [Painter] for the header icon to be displayed above the title.
 * @param status An optional [Status] to be displayed below the subtitle.
 * @param learnMoreClick An optional lambda to be invoked when the "Learn More" text is clicked.
 *
 * Asana Task: https://app.asana.com/1/137249556945/task/1213030562809173
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=13040-48441
 */
@Composable
fun DaxPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    body: String? = null,
    iconHeader: Painter? = null,
    status: Status? = null,
    learnMoreClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(DaxPageHeaderDefaults.containerPaddingValues),
        verticalArrangement = Arrangement.spacedBy(DaxPageHeaderDefaults.containerVerticalSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (iconHeader != null) {
            Image(
                painter = iconHeader,
                contentDescription = null,
                modifier = Modifier.size(
                    width = DaxPageHeaderDefaults.iconHeaderWidth,
                    height = DaxPageHeaderDefaults.iconHeaderHeight,
                ),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DaxPageHeaderDefaults.headerVerticalSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DaxText(
                text = title,
                style = DaxPageHeaderDefaults.titleTypography,
                color = DaxPageHeaderDefaults.titleColor,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                DaxText(
                    text = subtitle,
                    style = DaxPageHeaderDefaults.subtitleTypography,
                    color = DaxPageHeaderDefaults.subtitleColor,
                    textAlign = TextAlign.Center,
                )
            }
            if (status != null) {
                StatusIndicator(status = status)
            }
        }
        if (body != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DaxText(
                    text = body,
                    style = DaxPageHeaderDefaults.bodyTypography,
                    color = DaxPageHeaderDefaults.bodyColor,
                    textAlign = TextAlign.Center,
                )
                if (learnMoreClick != null) {
                    DaxText(
                        text = stringResource(R.string.pageHeaderLearnMore),
                        style = DaxPageHeaderDefaults.bodyTypography,
                        color = DaxPageHeaderDefaults.learnMoreColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(onClick = learnMoreClick),
                    )
                }
            }
        }
    }
}

object DaxPageHeaderDefaults {
    internal val iconHeaderHeight = 96.dp
    internal val iconHeaderWidth = 128.dp
    internal val containerVerticalSpacing = 8.dp
    internal val containerPaddingValues: PaddingValues
        get() = PaddingValues(all = 24.dp)
    internal val headerVerticalSpacing = 4.dp
    internal val titleTypography: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.h2
    internal val subtitleTypography: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.caption
    internal val bodyTypography: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body2
    internal val titleColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.primary
    internal val subtitleColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.secondary
    internal val bodyColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.secondary
    internal val learnMoreColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.brand.accentBlue
}

@PreviewLightDark
@Composable
private fun DaxPageHeaderTitleOnlyPreview() {
    PreviewBox {
        DaxPageHeader(title = "Privacy Protection")
    }
}

@PreviewLightDark
@Composable
private fun DaxPageHeaderNoIconPreview() {
    PreviewBox {
        DaxPageHeader(
            title = "Privacy Protection",
            subtitle = "Your privacy is our priority. We help you stay safe online with built-in tracking protection and private search.",
            body = "Privacy Pro is actively protecting you from trackers on the web and in apps.",
            status = Status.ALWAYS_ON,
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPageHeaderNoStatusPreview() {
    PreviewBox {
        DaxPageHeader(
            title = "Privacy Protection",
            subtitle = "Your privacy is our priority. We help you stay safe online with built-in tracking protection and private search.",
            body = "Privacy Pro is actively protecting you from trackers on the web and in apps.",
            iconHeader = painterResource(R.drawable.ic_privacy_pro_128),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxPageHeaderPreview() {
    PreviewBox {
        DaxPageHeader(
            title = "Privacy Protection",
            subtitle = "Your privacy is our priority. We help you stay safe online with built-in tracking protection and private search.",
            body = "Privacy Pro is actively protecting you from trackers on the web and in apps.",
            iconHeader = painterResource(R.drawable.ic_privacy_pro_128),
            status = Status.OFF,
            learnMoreClick = { /*TODO*/ },
        )
    }
}
