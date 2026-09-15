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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
 * Banner summarising the current state of App Tracking Protection, with a trailing pictogram and
 * the whole surface acting as a single tap target.
 *
 * The banner owns its surface, shape, shadow and pictogram; the caller owns the copy and the outer
 * spacing, so place it inside whatever margin the host screen uses.
 *
 * @param text The message to display.
 * @param state Which pictogram to show alongside the text.
 * @param onClick Invoked when the banner is tapped.
 * @param modifier The [Modifier] to be applied to this banner.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973969
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=17712-63319
 */
@Composable
fun DaxAppTPBanner(
    text: String,
    state: DaxAppTPBannerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxAppTPBanner(
        text = AnnotatedString(text),
        state = state,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * [AnnotatedString] variant of [DaxAppTPBanner]. Use this when the message needs inline styling,
 * such as the bolded lead-in the App Tracking Protection states use.
 *
 * @param text The message to display, as an [AnnotatedString].
 * @see DaxAppTPBanner for the remaining parameters.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973969
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=17712-63319
 */
@Composable
fun DaxAppTPBanner(
    text: AnnotatedString,
    state: DaxAppTPBannerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = DaxAppTPBannerDefaults.shape,
        elevation = DaxCardElevation(dimensionResource(R.dimen.keyline_1)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.keyline_4)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.keyline_2)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DaxText(
                text = text,
                style = DuckDuckGoTheme.typography.body2,
                modifier = Modifier.weight(1f),
            )
            Image(
                painter = DaxAppTPBannerDefaults.pictogram(state),
                contentDescription = null,
                modifier = Modifier.size(DaxAppTPBannerDefaults.PictogramSize),
            )
        }
    }
}

/**
 * The pictogram shown by [DaxAppTPBanner]. Callers map their own product states onto these two:
 * protection is working, or it needs attention.
 */
@Stable
enum class DaxAppTPBannerState {
    Protected,
    Warning,
}

private object DaxAppTPBannerDefaults {
    val PictogramSize: Dp = 48.dp

    val shape: Shape
        @Composable
        get() = DuckDuckGoTheme.shapes.large

    @Composable
    fun pictogram(state: DaxAppTPBannerState): Painter = painterResource(
        when (state) {
            DaxAppTPBannerState.Protected -> R.drawable.ic_apptp_banner_default
            DaxAppTPBannerState.Warning -> R.drawable.ic_apptp_banner_warning
        },
    )
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerEnabledPreview() {
    PreviewBox {
        DaxAppTPBanner(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App Tracking Protection is enabled") }
                append(" and blocking tracking attempts across your apps.")
            },
            state = DaxAppTPBannerState.Protected,
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerTrackersBlockedPreview() {
    PreviewBox {
        DaxAppTPBanner(
            text = buildAnnotatedString {
                append("App Tracking Protection blocked 1,235 tracking attempts in ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Yelp and 14 other apps") }
                append(" (past hour).")
            },
            state = DaxAppTPBannerState.Protected,
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerDisabledPreview() {
    PreviewBox {
        DaxAppTPBanner(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("App Tracking Protection disabled.") }
                append("\nTap to continue blocking tracking attempts across your apps.")
            },
            state = DaxAppTPBannerState.Warning,
            onClick = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxAppTPBannerRevokedPreview() {
    PreviewBox {
        DaxAppTPBanner(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("A VPN app on your device disabled App Tracking Protection.") }
                append("\nTap to re-enable.")
            },
            state = DaxAppTPBannerState.Warning,
            onClick = {},
        )
    }
}

@PreviewFontScale
@Composable
private fun DaxAppTPBannerFontScalePreview() {
    PreviewBox {
        DaxAppTPBanner(
            text = "App Tracking Protection is enabled and blocking tracking attempts across your apps.",
            state = DaxAppTPBannerState.Protected,
            onClick = {},
        )
    }
}
