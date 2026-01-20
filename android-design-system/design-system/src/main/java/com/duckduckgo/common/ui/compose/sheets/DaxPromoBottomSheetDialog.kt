/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.compose.sheets

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.buttons.SmallGhostButton
import com.duckduckgo.common.ui.compose.buttons.SmallPrimaryButton
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R

/**
 * Dax Promo bottom sheet dialog for the DuckDuckGo design system that shows promotional content
 * anchored to the bottom of the screen.
 *
 * @param title The title text of the promo.
 * @param description The description text of the promo.
 * @param promoIcon The image painter to be displayed in the promo.
 * @param buttons The composable buttons to be displayed at the bottom of the promo.
 * @param onDismissRequest Callback invoked when the user tries to dismiss the bottom sheet.
 * @param modifier The [Modifier] to be applied to this bottom sheet.
 * @param sheetState The state of the bottom sheet.
 * @param sheetGesturesEnabled Controls whether the bottom sheet can be interacted with via
 * touch gestures.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211659112661228
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6550-54079
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxPromoBottomSheetDialog(
    title: String?,
    description: String,
    promoIcon: Painter?,
    buttons: (@Composable RowScope.() -> Unit)?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
) {
    DaxBottomSheetDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(space = DaxPromoBottomSheetDefaults.sheetVerticalGap),
                modifier = Modifier.padding(all = DaxPromoBottomSheetDefaults.sheetPadding),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = DaxPromoBottomSheetDefaults.sheetHorizontalGap),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (promoIcon != null) {
                        Image(
                            painter = promoIcon,
                            contentDescription = null,
                            modifier = Modifier.size(size = DaxPromoBottomSheetDefaults.imageSize),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(space = DaxPromoBottomSheetDefaults.contentSpacing),
                        content = {
                            if (title != null) {
                                DaxText(
                                    text = title,
                                    color = DuckDuckGoTheme.colors.text.primary,
                                    style = DuckDuckGoTheme.typography.h2,
                                )
                            }
                            DaxText(
                                text = description,
                                color = DuckDuckGoTheme.colors.text.primary,
                                style = DuckDuckGoTheme.typography.body1,
                            )
                        },
                    )
                }
                if (buttons != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            space = DaxPromoBottomSheetDefaults.buttonSpacing,
                            alignment = Alignment.End,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        content = buttons,
                    )
                }
            }
        },
    )
}

object DaxPromoBottomSheetDefaults {
    internal val sheetPadding = 24.dp
    internal val sheetVerticalGap = 16.dp
    internal val sheetHorizontalGap = 16.dp
    internal val imageSize = 48.dp
    internal val contentSpacing = 8.dp
    internal val buttonSpacing = 8.dp
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxPromoBottomSheetDialogPreview() {
    DuckDuckGoTheme {
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxPromoBottomSheetDialog(
                title = "Title",
                description = "Add our search widget to your home screen for quick access.",
                promoIcon = painterResource(R.drawable.ic_announce),
                buttons = {
                    SmallGhostButton(text = "Button", onClick = {})
                    SmallPrimaryButton(text = "Button", onClick = {})
                },
                onDismissRequest = {},
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxPromoBottomSheetDialogNoImagePreview() {
    DuckDuckGoTheme {
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxPromoBottomSheetDialog(
                title = "Title",
                description = "Add our search widget to your home screen for quick access.",
                promoIcon = null,
                buttons = {
                    SmallGhostButton(text = "Button", onClick = {})
                    SmallPrimaryButton(text = "Button", onClick = {})
                },
                onDismissRequest = {},
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxPromoBottomSheetDialogNoImageAndTitlePreview() {
    DuckDuckGoTheme {
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxPromoBottomSheetDialog(
                title = null,
                description = "Add our search widget to your home screen for quick access.",
                promoIcon = null,
                buttons = {
                    SmallGhostButton(text = "Button", onClick = {})
                    SmallPrimaryButton(text = "Button", onClick = {})
                },
                onDismissRequest = {},
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxPromoBottomSheetDialogNoButtonsPreview() {
    DuckDuckGoTheme {
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxPromoBottomSheetDialog(
                title = "Title",
                description = "Add our search widget to your home screen for quick access.",
                promoIcon = painterResource(R.drawable.ic_announce),
                buttons = null,
                onDismissRequest = {},
            )
        }
    }
}
