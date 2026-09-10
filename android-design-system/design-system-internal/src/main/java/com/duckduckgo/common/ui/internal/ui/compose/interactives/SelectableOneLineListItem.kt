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

package com.duckduckgo.common.ui.internal.ui.compose.interactives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.menus.DaxDropdownMenu
import com.duckduckgo.common.ui.compose.menus.DaxDropdownMenuItem
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun SelectableOneLineListItem(
    title: String,
    selected: String,
    options: List<String>,
    onClick: (option: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    DaxOneLineListItem(
        modifier = modifier,
        text = title,
        trailingIcon = {
            DaxDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                anchor = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(shape = DuckDuckGoTheme.shapes.small)
                            .clickable(onClick = { expanded = true })
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        DaxText(
                            text = selected,
                            style = DuckDuckGoTheme.typography.caption,
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_down_24),
                            contentDescription = null,
                            tint = DuckDuckGoTheme.colors.icons.primary,
                        )
                    }
                },
            ) {
                options.forEach {
                    DaxDropdownMenuItem(
                        text = it,
                        onClick = {
                            onClick(it)
                            expanded = false
                        },
                    )
                }
            }
        },
    )
}

@PreviewLightDark
@Composable
fun SelectableOneLineListItemPreview() {
    var selected by remember { mutableStateOf("Top") }
    PreviewBox {
        SelectableOneLineListItem(
            title = "Wave edge",
            selected = selected,
            options = listOf("Top", "Bottom", "Left", "Right"),
            onClick = { selected = it },
        )
    }
}
