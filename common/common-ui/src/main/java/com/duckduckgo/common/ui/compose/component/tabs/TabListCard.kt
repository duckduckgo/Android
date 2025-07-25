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

package com.duckduckgo.common.ui.compose.component.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.component.core.card.DuckDuckGoCard
import com.duckduckgo.mobile.android.R

@Composable
fun NewTabListCard(
    isCurrentTab: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionStatus: SelectionStatus? = null,
) {
    TabListItem(
        title = "New Tab", // TODO move string, it is in browser R
        url = null,
        faviconModel = R.drawable.ic_dax_icon,
        isUnreadIndicatorVisible = false,
        isCurrentTab = isCurrentTab,
        onTabClick = onTabClick,
        onCloseTabClick = onCloseTabClick,
        selectionStatus = selectionStatus,
        modifier = modifier,
    )
}

@Composable
fun WebTabListCard(
    title: String,
    faviconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    isCurrentTab: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    url: String? = null,
    selectionStatus: SelectionStatus? = null,
) {
    TabListItem(
        title = title,
        url = url,
        faviconModel = faviconModel,
        isUnreadIndicatorVisible = isUnreadIndicatorVisible,
        isCurrentTab = isCurrentTab,
        onTabClick = onTabClick,
        onCloseTabClick = onCloseTabClick,
        selectionStatus = selectionStatus,
        modifier = modifier,
    )
}

@Composable
fun TabListItem(
    title: String,
    url: String?,
    faviconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    isCurrentTab: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionStatus: SelectionStatus? = null,
) {
    val borderColor = if (isCurrentTab) {
        DuckDuckGoTheme.colors.lines
    } else {
        Color.Transparent
    }

    val borderWidth = if (isCurrentTab) {
        2.dp
    } else {
        0.dp
    }

    val shape = DuckDuckGoTheme.shapes.medium

    DuckDuckGoCard(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .graphicsLayer { clip = false }
            .drawBehind {
                val strokeWidth = borderWidth.toPx()
                val inflation = strokeWidth / 2

                val outline = shape.createOutline(
                    size = Size(size.width + strokeWidth, size.height + strokeWidth),
                    layoutDirection = layoutDirection,
                    density = this,
                )

                translate(left = -inflation, top = -inflation) {
                    drawOutline(
                        outline = outline,
                        color = borderColor,
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
        onClick = onTabClick,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabFavicon(
                iconModel = faviconModel,
                isUnreadIndicatorVisible = isUnreadIndicatorVisible,
            )

            Spacer(
                modifier = Modifier.width(
                    if (isUnreadIndicatorVisible) 6.dp else 8.dp,
                ),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = DuckDuckGoTheme.typography.h3,
                )

                if (url != null) {
                    Text(
                        text = url,
                        color = DuckDuckGoTheme.colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = DuckDuckGoTheme.typography.h4,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onCloseTabClick) {
                when (selectionStatus) {
                    SelectionStatus.SELECTED -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_blue_round_24),
                            contentDescription = "Deselect tab button",
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    SelectionStatus.NOT_SELECTED -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_shape_circle_24),
                            contentDescription = "Select tab button",
                            tint = DuckDuckGoTheme.colors.primaryIcon,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    null -> {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_solid_16),
                            contentDescription = "Close Tab",
                            tint = Color.Black.copy(alpha = 84f), // TODO Why is this icon black in both themes in the designs?
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WebTabListItemPreview() {
    DuckDuckGoTheme(isDesignExperimentEnabled = true) {
        WebTabListCard(
            title = "DuckDuckGo",
            url = "https://duckduckgo.com",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = true,
            onTabClick = {},
            onCloseTabClick = {},
        )
    }
}

@Preview
@Composable
private fun WebTabListNewTabPreview() {
    DuckDuckGoTheme(isDesignExperimentEnabled = true) {
        WebTabListCard(
            title = "DuckDuckGo",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = false,
            onTabClick = {},
            onCloseTabClick = {},
        )
    }
}

@Preview
@Composable
private fun WebTabListItemSelectedPreview() {
    DuckDuckGoTheme(isDesignExperimentEnabled = true) {
        WebTabListCard(
            title = "DuckDuckGo",
            url = "https://duckduckgo.com",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = true,
            onTabClick = {},
            onCloseTabClick = {},
        )
    }
}

@Preview
@Composable
private fun WebTabListItemLongTextPreview() {
    DuckDuckGoTheme(isDesignExperimentEnabled = true) {
        WebTabListCard(
            title = "DuckDuckGo DuckDuckGo DuckDuckGo DuckDuckGo DuckDuckGo",
            url = "https://duckduckgo.com/some/long/path/that/should/be/ellipsized",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = false,
            onTabClick = {},
            onCloseTabClick = {},
        )
    }
}

@Preview
@Composable
private fun WebTabListItemWithSelectionStatusPreview() {
    DuckDuckGoTheme(isDesignExperimentEnabled = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WebTabListCard(
                title = "Not Selected",
                url = "https://duckduckgo.com",
                faviconModel = R.drawable.ic_dax_icon,
                isUnreadIndicatorVisible = true,
                isCurrentTab = false,
                onTabClick = {},
                onCloseTabClick = {},
                selectionStatus = SelectionStatus.NOT_SELECTED,
            )
            WebTabListCard(
                title = "Selected",
                url = "https://duckduckgo.com",
                faviconModel = R.drawable.ic_dax_icon,
                isUnreadIndicatorVisible = true,
                isCurrentTab = false,
                onTabClick = {},
                onCloseTabClick = {},
                selectionStatus = SelectionStatus.SELECTED,
            )
        }
    }
}


