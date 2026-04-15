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

package com.duckduckgo.common.ui.compose.switch

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox
import com.duckduckgo.mobile.android.R

/**
 * DuckDuckGo design system composable switch component.
 *
 * Wraps Material3 [Switch] with DuckDuckGo theme colors matching the XML [com.duckduckgo.common.ui.view.DaxSwitch].
 *
 * @param checked whether the switch is checked
 * @param onCheckedChange callback invoked when the switch is toggled, or null to make the switch non-interactive
 * @param modifier the [Modifier] to apply
 * @param enabled whether the switch is enabled
 * @param interactionSource the [MutableInteractionSource] representing the stream of interactions for this switch
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1213690991241745?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=72-14&m=dev
 */
@Composable
fun DaxSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.alpha(if (enabled) 1f else DaxSwitchDefaults.disabledAlpha),
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = {
            Spacer(modifier = Modifier.size(DaxSwitchDefaults.thumbSize))
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = DaxSwitchDefaults.colors.thumb,
            checkedTrackColor = DaxSwitchDefaults.colors.trackOn,
            uncheckedThumbColor = DaxSwitchDefaults.colors.thumb,
            uncheckedTrackColor = DaxSwitchDefaults.colors.trackOff,
            uncheckedBorderColor = Color.Transparent,
            uncheckedIconColor = Color.Transparent,
            disabledCheckedThumbColor = DaxSwitchDefaults.colors.thumb,
            disabledCheckedTrackColor = DaxSwitchDefaults.colors.trackOn,
            disabledUncheckedThumbColor = DaxSwitchDefaults.colors.thumb,
            disabledUncheckedTrackColor = DaxSwitchDefaults.colors.trackOff,
            disabledUncheckedBorderColor = Color.Transparent,
            disabledUncheckedIconColor = Color.Transparent,
            disabledCheckedBorderColor = Color.Transparent,
            disabledCheckedIconColor = Color.Transparent,
        ),
    )
}

private object DaxSwitchDefaults {

    const val disabledAlpha = 0.4f

    val thumbSize: Dp
        @Composable
        get() = dimensionResource(R.dimen.daxSwitchThumbSize)

    val colors: DaxSwitchColors
        @Composable
        get() = DaxSwitchColors(
            trackOn = DuckDuckGoTheme.colors.system.switchTrackOn,
            trackOff = DuckDuckGoTheme.colors.system.switchTrackOff,
            thumb = DuckDuckGoTheme.colors.system.switchThumb,
        )
}

private data class DaxSwitchColors(
    val trackOn: Color,
    val trackOff: Color,
    val thumb: Color,
)

@PreviewLightDark
@Composable
private fun DaxSwitchAllStatesPreview() {
    PreviewBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxSwitch(checked = false, onCheckedChange = {})
            DaxSwitch(checked = true, onCheckedChange = {})
            DaxSwitch(checked = false, onCheckedChange = {}, enabled = false)
            DaxSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
