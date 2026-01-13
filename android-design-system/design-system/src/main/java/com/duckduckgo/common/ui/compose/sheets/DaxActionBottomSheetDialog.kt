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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.asTextStyle
import com.duckduckgo.mobile.android.R

/**
 * Bottom sheet dialog for presenting a list of actions to the user.
 *
 * @param title Optional title to be displayed at the top of the bottom sheet.
 * @param onDismissRequest Callback invoked when the user tries to dismiss the bottom sheet.
 * @param modifier The [Modifier] to be applied to this bottom sheet.
 * @param sheetState The state of the bottom sheet.
 * @param lazyColumnState The state of the internal LazyColumn.
 * @param sheetGesturesEnabled Controls whether the bottom sheet can be interacted with via
 * touch gestures.
 * @param verticalArrangement The vertical arrangement of the items in the LazyColumn.
 * @param horizontalAlignment The horizontal alignment of the items in the LazyColumn.
 * @param userScrollEnabled Controls whether the user can scroll the LazyColumn.
 * @param content The content of the bottom sheet, defined as a [LazyListScope].
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211659112661228
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6550-54079
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxActionBottomSheetDialog(
    title: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    lazyColumnState: LazyListState = rememberLazyListState(),
    sheetGesturesEnabled: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    DaxBottomSheetDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        content = {
            LazyColumn(
                state = lazyColumnState,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
                userScrollEnabled = userScrollEnabled,
                contentPadding = PaddingValues(vertical = DaxActionBottomSheetDefaults.sheetVerticalPadding),
                content = {
                    if (title != null) {
                        item {
                            DaxActionTitleBottomSheetDialog(text = title)
                        }
                    }
                    content()
                },
            )
        },
    )
}

@Composable
private fun DaxActionTitleBottomSheetDialog(
    text: String,
    modifier: Modifier = Modifier,
    contentColor: Color = DaxActionBottomSheetDefaults.contentColor,
    style: TextStyle = DaxActionBottomSheetDefaults.style,
) {
    val daxStyle = remember(style) { DuckDuckGoTextStyle(style) }
    DaxText(
        text = text,
        color = contentColor,
        style = daxStyle,
        modifier = modifier
            .padding(DaxActionBottomSheetDefaults.sheetActionTitleContentPadding),
    )
}

object DaxActionBottomSheetDefaults {
    internal val style: TextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.body1.asTextStyle

    internal val contentColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.secondary

    internal val sheetVerticalPadding = 10.dp
    internal val sheetActionTitleContentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxActionBottomSheetDialogPreview() {
    DuckDuckGoTheme {
        val colors = ListItemDefaults.colors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.surface,
            headlineColor = DuckDuckGoTheme.colors.text.primary,
            leadingIconColor = DuckDuckGoTheme.colors.text.primary,
            trailingIconColor = DuckDuckGoTheme.colors.text.primary,
        )
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxActionBottomSheetDialog(
                title = "Actions",
                onDismissRequest = {},
                content = {
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ai_chat_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            colors = colors,
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            colors = colors,
                        )
                    }
                },
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxActionBottomSheetDialogNoIconsPreview() {
    DuckDuckGoTheme {
        val colors = ListItemDefaults.colors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.surface,
            headlineColor = DuckDuckGoTheme.colors.text.primary,
            leadingIconColor = DuckDuckGoTheme.colors.text.primary,
            trailingIconColor = DuckDuckGoTheme.colors.text.primary,
        )
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxActionBottomSheetDialog(
                title = "Actions",
                onDismissRequest = {},
                content = {
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            colors = colors,
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            colors = colors,
                        )
                    }
                },
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxActionBottomSheetDialogNoTitlePreview() {
    DuckDuckGoTheme {
        val colors = ListItemDefaults.colors(
            containerColor = DuckDuckGoTheme.colors.backgrounds.surface,
            headlineColor = DuckDuckGoTheme.colors.text.primary,
            leadingIconColor = DuckDuckGoTheme.colors.text.primary,
            trailingIconColor = DuckDuckGoTheme.colors.text.primary,
        )
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxActionBottomSheetDialog(
                title = null,
                onDismissRequest = {},
                content = {
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ai_chat_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            colors = colors,
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { DaxText("Label") },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24_solid_color),
                                    contentDescription = null,
                                )
                            },
                            colors = colors,
                        )
                    }
                },
            )
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun DaxActionBottomSheetDialogEmptyPreview() {
    DuckDuckGoTheme {
        Scaffold(
            containerColor = DuckDuckGoTheme.colors.backgrounds.background,
            contentColor = DuckDuckGoTheme.colors.text.primary,
        ) {
            DaxActionBottomSheetDialog(
                title = "Actions",
                onDismissRequest = {},
                content = {
                },
            )
        }
    }
}
