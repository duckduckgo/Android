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

package com.duckduckgo.common.ui.compose.message

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxCardElevation
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * Banner reporting that App Tracking Protection is on, with a leading checkmark pictogram and the
 * whole surface acting as a single tap target.
 *
 * The banner owns its surface, shape, shadow and pictogram; the caller owns the copy and the outer
 * spacing, so place it inside whatever margin the host screen uses.
 *
 * @param text The message to display.
 * @param onClick Invoked when the banner is tapped.
 * @param modifier The [Modifier] to be applied to this banner.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973969
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=17712-63319
 */
@Composable
fun DaxAppTPBannerEnabled(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxAppTPBannerEnabled(
        text = AnnotatedString(text),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * [AnnotatedString] variant of [DaxAppTPBannerEnabled]. Use this when the message needs inline
 * styling, such as the bolded lead-in the App Tracking Protection states use.
 *
 * @param text The message to display, as an [AnnotatedString].
 * @see DaxAppTPBannerEnabled for the remaining parameters.
 */
@Composable
fun DaxAppTPBannerEnabled(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxAppTPBanner(
        text = text,
        pictogram = painterResource(id = R.drawable.shield_check_recolorable_24),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Banner reporting that App Tracking Protection needs attention, with a leading warning pictogram
 * and the whole surface acting as a single tap target.
 *
 * The banner owns its surface, shape, shadow and pictogram; the caller owns the copy and the outer
 * spacing, so place it inside whatever margin the host screen uses.
 *
 * @param text The message to display.
 * @param onClick Invoked when the banner is tapped.
 * @param modifier The [Modifier] to be applied to this banner.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973969
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=17712-63319
 */
@Composable
fun DaxAppTPBannerDisabled(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxAppTPBannerDisabled(
        text = AnnotatedString(text),
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * [AnnotatedString] variant of [DaxAppTPBannerDisabled]. Use this when the message needs inline
 * styling, such as the bolded lead-in the App Tracking Protection states use.
 *
 * @param text The message to display, as an [AnnotatedString].
 * @see DaxAppTPBannerDisabled for the remaining parameters.
 */
@Composable
fun DaxAppTPBannerDisabled(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxAppTPBanner(
        text = text,
        pictogram = painterResource(id = R.drawable.exclamation_recolorable_24),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun DaxAppTPBanner(
    text: AnnotatedString,
    pictogram: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = DaxAppTPBannerDefaults.shape,
        elevation = DaxCardElevation(DaxAppTPBannerDefaults.elevation),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues = DaxAppTPBannerDefaults.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(DaxAppTPBannerDefaults.horizontalSpacing),
            verticalAlignment = Alignment.Top,
        ) {
            Image(
                painter = pictogram,
                contentDescription = null,
                modifier = Modifier.size(DaxAppTPBannerDefaults.PictogramSize),
            )
            DaxText(
                text = text,
                style = DuckDuckGoTheme.typography.body2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private object DaxAppTPBannerDefaults {
    val PictogramSize: Dp = 24.dp

    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.large

    val elevation: Dp
        @Composable
        get() = dimensionResource(R.dimen.keyline_1)

    val contentPadding: PaddingValues
        @Composable
        get() = PaddingValues(dimensionResource(R.dimen.keyline_4))

    val horizontalSpacing: Dp
        @Composable
        get() = dimensionResource(R.dimen.keyline_2)
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerEnabledPreview() {
    PreviewBox {
        DaxAppTPBannerEnabled(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App Tracking Protection is enabled") }
                append(" and blocking tracking attempts across your apps.")
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerTrackersBlockedPreview() {
    PreviewBox {
        DaxAppTPBannerEnabled(
            text = buildAnnotatedString {
                append("App Tracking Protection blocked 1,235 tracking attempts in ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Yelp and 14 other apps") }
                append(" (past hour).")
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerDisabledPreview() {
    PreviewBox {
        DaxAppTPBannerDisabled(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App Tracking Protection disabled.") }
                append("\nTap to continue blocking tracking attempts across your apps.")
            },
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerRevokedPreview() {
    PreviewBox {
        DaxAppTPBannerDisabled(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("A VPN app on your device disabled App Tracking Protection.") }
                append("\nTap to re-enable.")
            },
            onClick = {},
        )
    }
}

@PreviewFontScale
@Composable
private fun DaxAppTPBannerFontScalePreview() {
    PreviewBox {
        DaxAppTPBannerEnabled(
            text = "App Tracking Protection is enabled and blocking tracking attempts across your apps.",
            onClick = {},
        )
    }
}
