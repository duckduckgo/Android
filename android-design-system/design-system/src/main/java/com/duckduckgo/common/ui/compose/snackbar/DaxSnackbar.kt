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

package com.duckduckgo.common.ui.compose.snackbar

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.message.DaxAction
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.tools.PreviewBox

/**
 * Snackbar component for the DuckDuckGo design system.
 *
 * Renders a surface-coloured card with a body message and an optional trailing action, matching the
 * DDG snackbar styling (light surface background, primary text, accent-blue action) rather than the
 * dark Material default.
 *
 * This overload renders the snackbar directly — useful for previews, the design system catalog, or
 * any place that owns its own visibility. To show a snackbar transiently from a screen, host it with
 * a [SnackbarHost] and use the [DaxSnackbar] overload that accepts [SnackbarData]: duration,
 * placement and swipe-to-dismiss are owned by the host's [SnackbarHostState], not this composable.
 *
 * @param message The message text. May wrap onto multiple lines.
 * @param modifier The [Modifier] to be applied to this snackbar.
 * @param action Optional trailing action (e.g. "Undo"). When null, only the message is shown.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973944
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6550-55049
 */
@Composable
fun DaxSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    action: DaxAction? = null,
) {
    Snackbar(
        modifier = modifier,
        action = action?.let { daxAction ->
            {
                DaxGhostButton(
                    text = daxAction.text,
                    onClick = daxAction.onClick,
                )
            }
        },
        shape = DaxSnackbarDefaults.shape,
        containerColor = DaxSnackbarDefaults.containerColor,
        contentColor = DaxSnackbarDefaults.contentColor,
    ) {
        DaxText(
            text = message,
            style = DuckDuckGoTheme.typography.body2,
            color = DaxSnackbarDefaults.contentColor,
        )
    }
}

/**
 * [SnackbarData] overload of [DaxSnackbar] for use inside a [SnackbarHost]:
 *
 * ```
 * SnackbarHost(hostState) { data -> DaxSnackbar(data) }
 * ```
 *
 * Reads the message and optional action label from [snackbarData] and delegates to the visual
 * [DaxSnackbar] overload; tapping the action invokes [SnackbarData.performAction]. Trigger snackbars
 * via [SnackbarHostState.showSnackbar]. A dismiss action ([SnackbarData]'s `withDismissAction`) is
 * not rendered — the DDG snackbar design exposes only the message and an optional action.
 *
 * @param snackbarData The data provided by the host's [SnackbarHostState].
 * @param modifier The [Modifier] to be applied to this snackbar.
 *
 * Asana Task: https://app.asana.com/1/137249556945/project/1215496415658080/task/1211670072973944
 * Figma reference: https://www.figma.com/design/BOHDESHODUXK7wSRNBOHdu/%F0%9F%A4%96-Android-Components?node-id=6550-55049
 */
@Composable
fun DaxSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val visuals = snackbarData.visuals
    DaxSnackbar(
        message = visuals.message,
        modifier = modifier,
        action = visuals.actionLabel?.let { label ->
            DaxAction(text = label, onClick = { snackbarData.performAction() })
        },
    )
}

private object DaxSnackbarDefaults {
    val shape: Shape
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.shapes.small

    val containerColor: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.colors.backgrounds.surface

    val contentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = DuckDuckGoTheme.textColors.primary
}

@PreviewLightDark
@Composable
private fun DaxSnackbarMessageOnlyPreview() {
    PreviewBox {
        DaxSnackbar(
            message = "Text of the Snackbar can span to several lines.",
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSnackbarWithActionPreview() {
    PreviewBox {
        DaxSnackbar(
            message = "Text of the Snackbar can span to several lines.",
            action = DaxAction(text = "Undo", onClick = {}),
        )
    }
}

@PreviewLightDark
@Composable
private fun DaxSnackbarLongMessagePreview() {
    PreviewBox {
        DaxSnackbar(
            message = "This is a much longer snackbar message designed to wrap onto multiple lines so the " +
                "multi-line layout can be verified alongside a trailing action.",
            action = DaxAction(text = "Action", onClick = {}),
        )
    }
}
