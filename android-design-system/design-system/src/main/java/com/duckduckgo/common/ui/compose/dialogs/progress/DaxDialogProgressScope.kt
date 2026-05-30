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

package com.duckduckgo.common.ui.compose.dialogs.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.duckduckgo.common.ui.compose.cards.DaxSurface
import com.duckduckgo.common.ui.compose.text.DaxText

object DaxDialogProgressScope {
    @Composable
    fun DaxProgressBarOf3(
        current: Int,
        modifier: Modifier = Modifier,
    ) {
        DaxSurface(
            modifier = modifier,
            border = DaxProgressBarDefault.border,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(paddingValues = DaxProgressBarDefault.contentPadding)
                    .widthIn(min = DaxProgressBarDefault.minWidthOf3),
            ) {
                DotProgressBar(current = current, total = 3)
                DaxText(
                    text = "$current of 3",
                    style = DaxProgressBarDefault.textStyle,
                )
            }
        }
    }

    @Composable
    fun DaxProgressBarOf5(
        current: Int,
        modifier: Modifier = Modifier,
    ) {
        DaxSurface(
            modifier = modifier,
            border = DaxProgressBarDefault.border,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(paddingValues = DaxProgressBarDefault.contentPadding)
                    .widthIn(min = DaxProgressBarDefault.minWidthOf5),
            ) {
                DotProgressBar(current = current, total = 5)
                DaxText(
                    text = "$current of 5",
                    style = DaxProgressBarDefault.textStyle,
                )
            }
        }
    }
}
