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

import android.R.attr.mode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.component.core.card.DuckDuckGoCard
import com.duckduckgo.mobile.android.R
import java.io.File

@Composable
fun NewTabGridCard(
    isSelected: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TabCard(
        title = "New Tab",// TODO move string, it is in browser R
        faviconModel = R.drawable.ic_dax_icon,
        isUnreadIndicatorVisible = false,
        isCurrentTab = isSelected,
        onTabClick = onTabClick,
        onCloseTabClick = onCloseTabClick,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(dimensionResource(R.dimen.gridItemPreviewHeightNew))
                .fillMaxWidth(),
        ) {
            AsyncImage(
                model = R.drawable.ic_dax_icon,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.Center,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NewTabCardPreview() {
    DuckDuckGoTheme {
        NewTabGridCard(
            isSelected = false,
            onTabClick = {},
            onCloseTabClick = {},
        )
    }
}

@Composable
fun WebTabGridCard(
    title: String,
    faviconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    isCurrentTab: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    tabPreviewFile: File?,
    modifier: Modifier = Modifier,
    selectionStatus: SelectionStatus? = null,
) {
    TabCard(
        title = title,
        faviconModel = faviconModel,
        isUnreadIndicatorVisible = isUnreadIndicatorVisible,
        isCurrentTab = isCurrentTab,
        onTabClick = onTabClick,
        onCloseTabClick = onCloseTabClick,
        selectionStatus = selectionStatus,
        modifier = modifier,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(tabPreviewFile)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopStart,
            modifier = Modifier
                .height(dimensionResource(R.dimen.gridItemPreviewHeightNew))
                .clip(DuckDuckGoTheme.shapes.small)
                .fillMaxWidth(),
        )
    }
}

enum class SelectionStatus {
    SELECTED,
    NOT_SELECTED,
}

@PreviewLightDark
@Composable
private fun WebTabCardPreview() {
    DuckDuckGoTheme {
        WebTabGridCard(
            title = "DuckDuckGo",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = false,
            onTabClick = {},
            onCloseTabClick = {},
            tabPreviewFile = null,
        )
    }
}

@Composable
fun TabCard(
    title: String,
    faviconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    isCurrentTab: Boolean,
    onTabClick: () -> Unit,
    onCloseTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionStatus: SelectionStatus? = null,
    content: @Composable () -> Unit,
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
        Column(modifier = Modifier.padding(4.dp)) {
            TopBar(
                leadingIconModel = faviconModel,
                isUnreadIndicatorVisible = isUnreadIndicatorVisible,
                title = title,
                trailingIcon = {
                    when (selectionStatus) {
                        SelectionStatus.SELECTED -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_blue_24),
                                contentDescription = "Deselect tab button",
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        SelectionStatus.NOT_SELECTED -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_shape_circle_24),
                                contentDescription = "Select tab button",
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
                },
                onTrailingIconClick = onCloseTabClick,
            )
            content()
        }
    }
}

@PreviewLightDark
@Composable
private fun TabCardPreview() {
    DuckDuckGoTheme {
        TabCard(
            title = "DuckDuckGo",
            faviconModel = R.drawable.ic_dax_icon,
            isUnreadIndicatorVisible = true,
            isCurrentTab = false,
            onTabClick = {},
            onCloseTabClick = {},
        ) {
            Box(
                modifier = Modifier
                    .height(dimensionResource(R.dimen.gridItemPreviewHeightNew))
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopBar(
    leadingIconModel: Any?,
    isUnreadIndicatorVisible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable () -> Unit = {},
    onTrailingIconClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 4.dp)
            .defaultMinSize(minHeight = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabFavicon(
            iconModel = leadingIconModel,
            isUnreadIndicatorVisible = isUnreadIndicatorVisible,
        )

        Spacer(
            modifier = Modifier.width(
                if (isUnreadIndicatorVisible) 6.dp else 8.dp,
            ),
        )

        Text(
            text = title,
            color = DuckDuckGoTheme.colors.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            style = DuckDuckGoTheme.typography.h5,
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onTrailingIconClick,
            modifier = Modifier
                .size(24.dp),
        ) {
            trailingIcon()
        }
    }
}

@PreviewLightDark
@Composable
private fun TopBarPreview() {
    DuckDuckGoTheme {
        Box(
            modifier = Modifier.background(color = DuckDuckGoTheme.colors.background),
        ) {
            TopBar(
                leadingIconModel = R.drawable.ic_dax_icon,
                title = "DuckDuckGo",
                isUnreadIndicatorVisible = true,
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_solid_16),
                        contentDescription = "Close Tab",
                        tint = DuckDuckGoTheme.colors.primaryIcon,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onTrailingIconClick = {},
            )
        }
    }
}
