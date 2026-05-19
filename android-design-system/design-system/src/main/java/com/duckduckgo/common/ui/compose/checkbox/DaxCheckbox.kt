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

package com.duckduckgo.common.ui.compose.checkbox

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system composable checkbox component.
 *
 * Wraps Material3 [Checkbox] with DuckDuckGo theme colors.
 *
 * @param checked whether the checkbox is checked
 * @param onCheckedChange callback invoked when the checkbox is toggled, or null to make the checkbox non-interactive
 * @param modifier the [Modifier] to apply
 * @param enabled whether the checkbox is enabled
 * @param interactionSource the [MutableInteractionSource] representing the stream of interactions for this checkbox
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214927615452777?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=951-1&m=dev
 */
@Composable
fun DaxCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.alpha(if (enabled) 1f else DaxCheckboxDefaults.disabledAlpha),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = CheckboxDefaults.colors(
            checkedColor = DaxCheckboxDefaults.colors.boxOn,
            uncheckedColor = DaxCheckboxDefaults.colors.boxOff,
            checkmarkColor = DaxCheckboxDefaults.colors.mark,
            disabledCheckedColor = DaxCheckboxDefaults.colors.boxOn,
            disabledUncheckedColor = DaxCheckboxDefaults.colors.boxOff,
            disabledIndeterminateColor = DaxCheckboxDefaults.colors.boxOn,
        ),
    )
}

private object DaxCheckboxDefaults {

    const val disabledAlpha = 0.4f

    val colors: DaxCheckboxColors
        @Composable
        get() = DaxCheckboxColors(
            boxOn = DuckDuckGoTheme.colors.system.checkboxOn,
            boxOff = DuckDuckGoTheme.colors.system.checkboxOff,
            mark = DuckDuckGoTheme.colors.system.checkboxMark,
        )
}

private data class DaxCheckboxColors(
    val boxOn: Color,
    val boxOff: Color,
    val mark: Color,
)

@PreviewLightDark
@Composable
private fun DaxCheckboxAllStatesPreview() {
    PreviewBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxCheckbox(checked = false, onCheckedChange = {})
            DaxCheckbox(checked = true, onCheckedChange = {})
            DaxCheckbox(checked = false, onCheckedChange = {}, enabled = false)
            DaxCheckbox(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}
