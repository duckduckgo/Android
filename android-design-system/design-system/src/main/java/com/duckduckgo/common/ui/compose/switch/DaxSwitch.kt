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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system composable switch component.
 *
 * Wraps Material3 [Switch] with DuckDuckGo theme colors matching the XML [com.duckduckgo.common.ui.view.DaxSwitch].
 *
 * @param checked whether the switch is checked
 * @param onCheckedChange callback invoked when the switch is toggled, or null to make the switch non-interactive
 * @param modifier the [Modifier] to apply
 * @param enabled whether the switch is enabled
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
) {
    val trackOn = DuckDuckGoTheme.colors.system.switchTrackOn
    val trackOff = DuckDuckGoTheme.colors.system.switchTrackOff
    val thumb = DuckDuckGoTheme.colors.system.switchThumb

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = {
            Spacer(modifier = Modifier.size(32.dp))
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = thumb,
            checkedTrackColor = trackOn,
            uncheckedThumbColor = thumb,
            uncheckedTrackColor = trackOff,
            uncheckedBorderColor = Color.Transparent,
            uncheckedIconColor = Color.Transparent,
            disabledCheckedThumbColor = thumb.copy(alpha = thumb.alpha * 0.38f),
            disabledCheckedTrackColor = trackOff.copy(alpha = trackOff.alpha * 0.5f),
            disabledUncheckedThumbColor = thumb.copy(alpha = thumb.alpha * 0.38f),
            disabledUncheckedTrackColor = trackOff.copy(alpha = trackOff.alpha * 0.5f),
            disabledUncheckedBorderColor = Color.Transparent,
            disabledUncheckedIconColor = Color.Transparent,
        ),
    )
}

@PreviewLightDark
@Composable
private fun DaxSwitchUncheckedPreview() {
    PreviewBox {
        DaxSwitch(checked = false, onCheckedChange = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxSwitchCheckedPreview() {
    PreviewBox {
        DaxSwitch(checked = true, onCheckedChange = {})
    }
}

@PreviewLightDark
@Composable
private fun DaxSwitchDisabledUncheckedPreview() {
    PreviewBox {
        DaxSwitch(checked = false, onCheckedChange = {}, enabled = false)
    }
}

@PreviewLightDark
@Composable
private fun DaxSwitchDisabledCheckedPreview() {
    PreviewBox {
        DaxSwitch(checked = true, onCheckedChange = {}, enabled = false)
    }
}

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
