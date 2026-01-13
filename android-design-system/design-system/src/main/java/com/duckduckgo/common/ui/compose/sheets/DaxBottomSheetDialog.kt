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

package com.duckduckgo.common.ui.compose.sheets

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.theme.Black
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme

/**
 * Base bottom sheet dialog for the DuckDuckGo design system that show secondary content anchored
 * to the bottom of the screen.
 *
 * @param onDismissRequest Callback invoked when the user tries to dismiss the bottom sheet.
 * @param modifier The [Modifier] to be applied to this bottom sheet.
 * @param sheetState The state of the bottom sheet.
 * @param shape The shape of the bottom sheet container.
 * @param containerColor The background color of the bottom sheet container.
 * @param contentColor The content color of the bottom sheet.
 * @param scrimColor The color of the scrim that obscures content outside the bottom sheet.
 * @param sheetGesturesEnabled Controls whether the bottom sheet can be interacted with via
 * touch gestures.
 * @param content The content of the bottom sheet.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1202857801505092/task/1211659112661228
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6550-54079
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaxBottomSheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = DaxBottomSheetDefaults.shape,
    containerColor: Color = DaxBottomSheetDefaults.containerColor,
    contentColor: Color = DaxBottomSheetDefaults.contentColor,
    scrimColor: Color = DaxBottomSheetDefaults.scrimColor,
    sheetGesturesEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        scrimColor = scrimColor,
        dragHandle = null,
        content = content,
    )
}

object DaxBottomSheetDefaults {
    val containerColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.backgrounds.surface

    val contentColor: Color
        @Composable
        get() = DuckDuckGoTheme.colors.text.primary

    val scrimColor: Color
        @Composable get() = Black.copy(alpha = .6f)

    val shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}
