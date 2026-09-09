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

package com.duckduckgo.common.ui.internal.ui.component.templates

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.Status
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBar
import com.duckduckgo.common.ui.compose.appbars.DaxTopAppBarNavigationIcon
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenuIconButton
import com.duckduckgo.common.ui.compose.template.DaxPageHeader
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.mobile.android.R as CommonR

@Composable
fun TemplatesPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyline4 = dimensionResource(CommonR.dimen.keyline_4)
    val state = rememberLazyListState()
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    LazyColumn(
        modifier = modifier
            .nestedScroll(nestedScrollInterop)
            .background(color = DuckDuckGoTheme.colors.backgrounds.background),
        state = state,
        contentPadding = PaddingValues(all = keyline4),
        verticalArrangement = Arrangement.spacedBy(space = keyline4),
    ) {
        item {
            DaxText(
                text = "Page Header (Settings)",
                style = DuckDuckGoTheme.typography.h4,
                color = DuckDuckGoTheme.colors.text.tertiary,
                modifier = Modifier.padding(vertical = keyline4),
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                status = Status.Off,
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                status = Status.On,
                iconHeader = painterResource(CommonR.drawable.ic_privacy_pro_128),
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                status = Status.AlwaysOn,
                iconHeader = painterResource(CommonR.drawable.ic_privacy_pro_128),
                body = "DuckDuckGo Private Search is your default search engine, so you can search the web without being tracked.",
            )
        }
        item {
            DaxPageHeader(
                title = "Private Search",
                subtitle = "Lorem impsum dolor sit amet",
                status = Status.AlwaysOn,
                iconHeader = painterResource(CommonR.drawable.ic_privacy_pro_128),
                body = "DuckDuckGo Private Search is your default search engine, so you can search the web without being tracked.",
                learnMoreClick = {
                    Toast.makeText(context, "Learn more clicked", Toast.LENGTH_SHORT).show()
                },
            )
        }
        item {
            DaxText(
                text = "Top App Bar with a context menu",
                style = DuckDuckGoTheme.typography.h4,
                color = DuckDuckGoTheme.colors.text.tertiary,
                modifier = Modifier.padding(vertical = keyline4),
            )
        }
        item {
            DaxTopAppBar(
                title = "Bookmarks",
                navigationIcon = DaxTopAppBarNavigationIcon.Back { },
                actions = {
                    DaxIconButton(
                        onClick = { },
                        iconPainter = painterResource(CommonR.drawable.ic_find_search_24),
                        contentDescription = "Search",
                    )
                    DaxContextMenuIconButton(
                        contentDescription = "More options",
                    ) {
                        DaxIconItem(
                            text = "Bookmark",
                            painterLeadingIcon = painterResource(CommonR.drawable.ic_bookmark_24),
                            onClick = {
                                Toast.makeText(context, "Bookmark clicked", Toast.LENGTH_SHORT).show()
                            },
                            showDivider = true,
                        )
                        DaxInsetItem(
                            text = "Copy link",
                            onClick = {
                                Toast.makeText(context, "Copy link clicked", Toast.LENGTH_SHORT).show()
                            },
                            trailingIcon = { Icon(painterResource(CommonR.drawable.ic_copy_24), null) },
                            showDivider = true,
                        )
                        DaxDefaultItem(
                            text = "Unavailable",
                            onClick = { },
                            enabled = false,
                            showDivider = true,
                        )
                        DaxDefaultItem(
                            text = "Delete",
                            onClick = {
                                Toast.makeText(context, "Delete clicked", Toast.LENGTH_SHORT).show()
                            },
                            isDestructive = true,
                        )
                    }
                },
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
