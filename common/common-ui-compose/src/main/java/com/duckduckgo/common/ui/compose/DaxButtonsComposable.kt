/*
 * Copyright (c) 2023 DuckDuckGo
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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.ButtonStyle.ALT
import com.duckduckgo.common.ui.compose.ButtonStyle.DEFAULT
import com.duckduckgo.common.ui.compose.ButtonStyle.DESTRUCTIVE
import com.duckduckgo.common.ui.compose.ui.DaxButtonShape
import com.duckduckgo.common.ui.compose.ui.DaxColor

@Preview
@Composable
fun DaxButtonPrimary(
    modifier: Modifier = Modifier,
    buttonSize: ButtonSize = ButtonSize.LARGE,
    buttonStyle: ButtonStyle = ButtonStyle.DEFAULT,
    text: String = "test",
    iconRes: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val containerColor = when(buttonStyle) {
        DEFAULT -> DaxColor.ButtonDefault
        DESTRUCTIVE -> DaxColor.ButtonDestructive
        ALT -> DaxColor.ButtonAlt
    }

    Button(
        modifier = modifier,
        onClick = { onClick.invoke() },
        enabled = enabled,
        shape = DaxButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = DaxColor.ButtonPrimaryTextColor,
        ),
    ) {
        DaxButtonContent(text, iconRes)
    }
}

@Composable
private fun DaxButtonContent(text: String, iconRes: Int?) {
    if (iconRes != null) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    }
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

enum class ButtonSize {
    SMALL,
    LARGE,
}

enum class ButtonStyle {
    DEFAULT,
    DESTRUCTIVE,
    ALT,
}
