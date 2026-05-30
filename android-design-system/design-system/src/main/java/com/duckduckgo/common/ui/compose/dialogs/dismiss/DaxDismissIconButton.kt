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

package com.duckduckgo.common.ui.compose.dialogs.dismiss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

@Composable
fun DaxDismissIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        modifier = modifier.size(32.dp),
        onClick = onClick,
        colors = DismissIconButtonDefaults.colors,
        shape = CircleShape,
        border = DismissIconButtonDefaults.border,
        content = {
            Icon(
                painter = painterResource(R.drawable.ic_close_24_solid_color),
                contentDescription = stringResource(R.string.closeButtonContentDescription),
                modifier = Modifier.size(16.dp)
            )
        },
    )
}

private object DismissIconButtonDefaults {
    val colors: IconButtonColors
        @Composable
        get() = IconButtonDefaults.iconButtonColors(
            contentColor = DuckDuckGoTheme.colors.icons.primary,
            containerColor = DuckDuckGoTheme.colors.backgrounds.window,
        )

    val border: BorderStroke
        @Composable
        get() = BorderStroke(
            width = 1.5.dp,
            color = DuckDuckGoTheme.colors.brand.accentBrand20,
        )
}

@PreviewLightDark
@Composable
private fun DaxDismissIconButtonPreview() {
    PreviewBox {
        DaxDismissIconButton(onClick = {})
    }
}
