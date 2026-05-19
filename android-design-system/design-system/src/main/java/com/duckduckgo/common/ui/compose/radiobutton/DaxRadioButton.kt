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

package com.duckduckgo.common.ui.compose.radiobutton

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * DuckDuckGo design system composable radio button component.
 *
 * Wraps Material3 [RadioButton] with DuckDuckGo theme colors matching the XML
 * [com.duckduckgo.common.ui.view.button.RadioButton].
 *
 * @param selected whether the radio button is selected
 * @param onClick callback invoked when the radio button is clicked, or null to make the radio button non-interactive
 * @param modifier the [Modifier] to apply
 * @param enabled whether the radio button is enabled
 * @param interactionSource the [MutableInteractionSource] representing the stream of interactions for this radio button
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1214777812685547?focus=true
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=892-1080&m=dev
 */
@Composable
fun DaxRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = RadioButtonDefaults.colors(
            selectedColor = DaxRadioButtonDefaults.colors.selected,
            unselectedColor = DaxRadioButtonDefaults.colors.unselected,
            disabledSelectedColor = DaxRadioButtonDefaults.colors.disabled,
            disabledUnselectedColor = DaxRadioButtonDefaults.colors.disabled,
        ),
    )
}

private object DaxRadioButtonDefaults {

    val colors: DaxRadioButtonColors
        @Composable
        get() = DaxRadioButtonColors(
            selected = DuckDuckGoTheme.colors.brand.accentBlue,
            unselected = DuckDuckGoTheme.colors.brand.accentBlue,
            disabled = DuckDuckGoTheme.colors.backgrounds.containerDisabled,
        )
}

private data class DaxRadioButtonColors(
    val selected: Color,
    val unselected: Color,
    val disabled: Color,
)

@PreviewLightDark
@Composable
private fun DaxRadioButtonAllStatesPreview() {
    PreviewBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DaxRadioButton(selected = false, onClick = {})
            DaxRadioButton(selected = true, onClick = {})
            DaxRadioButton(selected = false, onClick = {}, enabled = false)
            DaxRadioButton(selected = true, onClick = {}, enabled = false)
        }
    }
}
