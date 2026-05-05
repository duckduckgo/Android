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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.cards.DaxCard
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
 * @param title The message title.
 * @param body The message body text.
 * @param illustration The illustration shown between title and body. Drawn at its
 *   intrinsic size with [androidx.compose.ui.layout.ContentScale.Fit]. Use
 *   [androidx.compose.ui.res.painterResource] for a local drawable, or
 *   `coil3.compose.rememberAsyncImagePainter` for a remote URL.
 * @param actionText The label of the primary action button.
 * @param onActionClick Called when the user taps the primary action.
 * @param onDismissed Called when the user taps the dismiss button.
 * @param modifier Modifier for this message card.
 */
@Composable
fun PromoSingleActionMessage(
    title: String,
    body: String,
    illustration: Painter,
    actionText: String,
    onActionClick: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DaxCard(
        modifier = modifier.padding(
            start = dimensionResource(R.dimen.keyline_4),
            end = dimensionResource(R.dimen.keyline_4),
            bottom = dimensionResource(R.dimen.keyline_4),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = DuckDuckGoTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimensionResource(R.dimen.keyline_4)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.closeButtonContentDescription),
                modifier = Modifier
                    .align(Alignment.End)
                    .size(dimensionResource(R.dimen.messageCtaCloseButtonSize))
                    .clip(CircleShape)
                    .clickable(
                        onClick = onDismissed,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .padding(
                        horizontal = dimensionResource(R.dimen.keyline_2),
                    ),
            )
            DaxText(
                text = title,
                style = DuckDuckGoTheme.typography.h2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(
                        top = dimensionResource(R.dimen.keyline_2),
                        start = dimensionResource(R.dimen.keyline_4),
                        end = dimensionResource(R.dimen.keyline_4),
                    )
                    .fillMaxWidth(),
            )
            Image(
                painter = illustration,
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
            DaxText(
                text = body,
                style = DuckDuckGoTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(
                        top = dimensionResource(R.dimen.keyline_2),
                        start = dimensionResource(R.dimen.keyline_4),
                        end = dimensionResource(R.dimen.keyline_4),
                    )
                    .fillMaxWidth(),
            )
            DaxPrimaryButton(
                text = actionText,
                onClick = onActionClick,
                size = DaxButtonSize.Small,
                leadingIconPainter = painterResource(R.drawable.ic_share_android_16),
                modifier = Modifier
                    .padding(top = dimensionResource(R.dimen.keyline_4), bottom = dimensionResource(R.dimen.keyline_2))
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PromoSingleActionMessagePreview() {
    PreviewBox {
        PromoSingleActionMessage(
            title = "Promo Single Action Message",
            body = "Body text goes here. This component has two buttons and showcases and app update",
            illustration = painterResource(R.drawable.promo_mac_and_windows),
            actionText = "Promo Link",
            onActionClick = {},
            onDismissed = {},
        )
    }
}
