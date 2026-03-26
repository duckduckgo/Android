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

package com.duckduckgo.common.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.AlertGreen
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.Gray50
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DaxStatusIndicator component is representing one of three states: Always On, On, or Off.
 *
 * @param status The [Status] to represent.
 * @param modifier The [Modifier] to be applied to this status indicator.
 */
@Composable
fun DaxStatusIndicator(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val (active, label) = when (status) {
        Status.AlwaysOn -> true to stringResource(R.string.alwaysOn)
        Status.On -> true to stringResource(R.string.on)
        Status.Off -> false to stringResource(R.string.off)
    }
    Row(
        modifier = modifier
            .heightIn(min = StatusIndicatorDefaults.minHeight)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(space = StatusIndicatorDefaults.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(StatusIndicatorDefaults.iconSize)
                .clip(CircleShape)
                .background(
                    color = if (active) {
                        StatusIndicatorDefaults.activeDotColor
                    } else {
                        StatusIndicatorDefaults.disabledDotColor
                    },
                    shape = CircleShape,
                ),
        )
        DaxText(
            text = label,
            style = StatusIndicatorDefaults.typography,
            color = StatusIndicatorDefaults.contentColor,
        )
    }
}

/**
 * The possible statuses for the [DaxStatusIndicator].
 */
enum class Status {
    /**
     * Indicates that the status is always on.
     */
    AlwaysOn,

    /**
     * Indicates that the status is on.
     */
    On,

    /**
     * Indicates that the status is off.
     */
    Off,
}

internal object StatusIndicatorDefaults {
    internal val iconSize = 8.dp
    internal val spacing = 4.dp
    internal val minHeight = 16.dp

    internal val disabledDotColor: Color
        @Composable
        @ReadOnlyComposable
        get() = Gray50

    internal val activeDotColor: Color
        @Composable
        @ReadOnlyComposable
        get() = AlertGreen

    internal val contentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.text.secondary

    internal val typography: DuckDuckGoTextStyle
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.typography.caption
}

@PreviewLightDark
@Composable
private fun DaxStatusIndicatorPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DaxStatusIndicator(status = Status.AlwaysOn)
            DaxStatusIndicator(status = Status.On)
            DaxStatusIndicator(status = Status.Off)
        }
    }
}
