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

package com.duckduckgo.common.ui.compose.message.remote

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
import com.duckduckgo.common.ui.compose.cards.DaxCardElevation
import com.duckduckgo.common.ui.compose.message.DaxAction
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo promo message card with a single primary action.
 *
 * Marketing-style card with the dismiss button at the top, then title, full-width
 * illustration, body, and a primary action button with a leading share icon.
 *
 * @param title The message title. Supports HTML markup (`<b>`, `<i>`, `<a>`, etc.) —
 *   parsed via `AnnotatedString.fromHtml(...)`.
 * @param body The message body text. Supports HTML markup (`<b>`, `<i>`, `<a>`, etc.) —
 *   parsed via `AnnotatedString.fromHtml(...)`.
 * @param illustration The illustration shown between title and body. Drawn with
 *   [ContentScale.Fit] inside a 256dp × 144dp max box so remote images can't break
 *   the card layout. Use [painterResource] for a local drawable, or
 *   `coil3.compose.rememberAsyncImagePainter` for a remote URL.
 * @param illustrationContentDescription Accessibility description for [illustration].
 *   Pass `null` only if the illustration is purely decorative and adds no information
 *   beyond [title] and [body].
 * @param action The primary call-to-action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
@Composable
fun DaxPromoSingleActionMessage(
    title: String,
    body: String,
    illustration: Painter,
    illustrationContentDescription: String?,
    action: DaxAction,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxCard(
        modifier = modifier,
        elevation = DaxCardElevation(dimensionResource(R.dimen.keyline_1)),
        shape = DuckDuckGoTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DaxIconButton(
                onClick = onDismissed,
                iconPainter = painterResource(R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.closeButtonContentDescription),
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))
            DaxText(
                text = remember(title) { AnnotatedString.fromHtml(title) },
                style = DuckDuckGoTheme.typography.h2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.keyline_4))
                    .fillMaxWidth(),
            )
            Image(
                painter = illustration,
                contentDescription = illustrationContentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.sizeIn(
                    maxWidth = dimensionResource(R.dimen.promoMessageCardIllustrationMaxWidth),
                    maxHeight = dimensionResource(R.dimen.promoMessageCardIllustrationMaxHeight),
                ),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_2)))
            DaxText(
                text = remember(body) { AnnotatedString.fromHtml(body) },
                style = DuckDuckGoTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = dimensionResource(R.dimen.keyline_4))
                    .fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_4)))
            DaxPrimaryButton(
                text = action.text,
                onClick = action.onClick,
                size = DaxButtonSize.Small,
                leadingIconPainter = painterResource(R.drawable.ic_share_android_16),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.keyline_5)))
        }
    }
}

@PreviewLightDark
@Composable
private fun DaxPromoSingleActionMessagePreview() {
    PreviewBox {
        DaxPromoSingleActionMessage(
            title = "Promo Single Action Message",
            body = "Body text goes here. This component has two buttons and showcases an app update",
            illustration = painterResource(R.drawable.promo_mac_and_windows),
            illustrationContentDescription = null,
            action = DaxAction(text = "Promo Link", onClick = {}),
            onDismissed = {},
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.keyline_4),
                end = dimensionResource(R.dimen.keyline_4),
                bottom = dimensionResource(R.dimen.keyline_4),
            ),
        )
    }
}
