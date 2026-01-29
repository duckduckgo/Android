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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.AlertGreen
import com.duckduckgo.common.ui.compose.theme.DisabledColor
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTextStyle
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * A status indicator representing one of three states: Always On, On, or Off.
 *
 * @param status The [Status] to represent.
 * @param modifier The [Modifier] to be applied to this status indicator.
 */
@Composable
fun StatusIndicator(
    status: Status,
    modifier: Modifier = Modifier,
) {
    val (active, label) = when (status) {
        Status.ALWAYS_ON -> true to stringResource(R.string.alwaysOn)
        Status.ON -> true to stringResource(R.string.on)
        Status.OFF -> false to stringResource(R.string.off)
    }
    StatusIndicator(
        active = active,
        label = label,
        modifier = modifier,
    )
}

/**
 * A simple status indicator with a colored dot and a label.
 *
 * @param active Whether the status is active (true) or inactive (false).
 * @param label The label to display next to the dot.
 * @param modifier The [Modifier] to be applied to this status indicator.
 */
@Composable
fun StatusIndicator(
    active: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = StatusIndicatorDefaults.minHeight)
            .semantics(mergeDescendants = true) {
                this.text = AnnotatedString(label)
                this.selected = active
            },
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
                        StatusIndicatorDefaults.disableDotColor
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
 * The possible statuses for the [StatusIndicator].
 */
enum class Status {
    /**
     * Indicates that the status is always on.
     */
    ALWAYS_ON,

    /**
     * Indicates that the status is on.
     */
    ON,

    /**
     * Indicates that the status is off.
     */
    OFF,
}

object StatusIndicatorDefaults {
    internal val iconSize = 8.dp
    internal val spacing = 4.dp
    internal val minHeight = 16.dp

    internal val disableDotColor: Color
        @Composable
        get() = DisabledColor

    internal val activeDotColor: Color
        @Composable
        get() = AlertGreen

    internal val contentColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.secondary

    internal val typography: DuckDuckGoTextStyle
        @Composable
        get() = DuckDuckGoTheme.typography.caption
}

@PreviewLightDark
@Composable
private fun StatusIndicatorPreview() {
    PreviewBox {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusIndicator(
                active = true,
                label = "Always On",
            )
            StatusIndicator(
                active = true,
                label = "On",
            )
            StatusIndicator(
                active = false,
                label = "Off",
            )
        }
    }
}
