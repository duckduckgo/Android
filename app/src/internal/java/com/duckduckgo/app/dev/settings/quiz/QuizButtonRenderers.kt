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

package com.duckduckgo.app.dev.settings.quiz

import android.view.LayoutInflater
import androidx.annotation.LayoutRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxBrandButton
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostAltButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostButton
import com.duckduckgo.common.ui.compose.button.DaxDestructiveGhostSecondaryButton
import com.duckduckgo.common.ui.compose.button.DaxDestructivePrimaryButton
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButton
import com.duckduckgo.app.browser.R as AppR
import com.duckduckgo.mobile.android.R as DesignSystemR

internal val buttonQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.BrandButton to buttonRenderer(AppR.layout.quiz_view_brand_button) { label, size, enabled, icon ->
        DaxBrandButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.PrimaryButton to buttonRenderer(AppR.layout.quiz_view_primary_button) { label, size, enabled, icon ->
        DaxPrimaryButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.SecondaryButton to buttonRenderer(AppR.layout.quiz_view_secondary_button) { label, size, enabled, icon ->
        DaxSecondaryButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.DestructivePrimaryButton to buttonRenderer(AppR.layout.quiz_view_destructive_primary_button) { label, size, enabled, icon ->
        DaxDestructivePrimaryButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.GhostButton to buttonRenderer(AppR.layout.quiz_view_ghost_button) { label, size, enabled, icon ->
        DaxGhostButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.DestructiveGhostSecondaryButton to buttonRenderer(
        AppR.layout.quiz_view_destructive_ghost_secondary_button,
    ) { label, size, enabled, icon ->
        DaxDestructiveGhostSecondaryButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.DestructiveGhostButton to buttonRenderer(AppR.layout.quiz_view_destructive_ghost_button) { label, size, enabled, icon ->
        DaxDestructiveGhostButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
    QuestionId.DestructiveGhostAltButton to buttonRenderer(AppR.layout.quiz_view_destructive_ghost_alt_button) { label, size, enabled, icon ->
        DaxDestructiveGhostAltButton(text = label, onClick = {}, size = size, enabled = enabled, leadingIconPainter = icon)
    },
)

private fun buttonRenderer(
    @LayoutRes layoutId: Int,
    button: @Composable (label: String, size: DaxButtonSize, enabled: Boolean, icon: Painter?) -> Unit,
): QuizRenderer = quizRenderer(
    createView = { context -> LayoutInflater.from(context).inflate(layoutId, null, false) },
    composeSample = { ButtonExamples(button) },
)

@Composable
private fun ButtonExamples(button: @Composable (label: String, size: DaxButtonSize, enabled: Boolean, icon: Painter?) -> Unit) {
    val icon = painterResource(DesignSystemR.drawable.ic_add_24_solid_color)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        button("Small", DaxButtonSize.Small, true, null)
        button("Large with icon", DaxButtonSize.Large, true, icon)
        button("Disabled", DaxButtonSize.Small, false, null)
    }
}
