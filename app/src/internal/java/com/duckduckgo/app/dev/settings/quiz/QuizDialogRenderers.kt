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

import android.content.Context
import android.view.LayoutInflater
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.duckduckgo.common.ui.compose.button.DaxButtonSize
import com.duckduckgo.common.ui.compose.button.DaxGhostButton
import com.duckduckgo.common.ui.compose.button.DaxPrimaryButton
import com.duckduckgo.common.ui.compose.button.DaxSecondaryButton
import com.duckduckgo.common.ui.compose.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.compose.dialog.DaxPrimaryStackedButtons
import com.duckduckgo.common.ui.compose.dialog.DaxRadioOption
import com.duckduckgo.common.ui.compose.dialog.DaxRadioOptions
import com.duckduckgo.common.ui.compose.dialog.DaxStackedButton
import com.duckduckgo.common.ui.compose.dialog.DaxTextAlertDialog
import com.duckduckgo.common.ui.compose.listitem.DaxOneLineListItem
import com.duckduckgo.common.ui.compose.sheets.DaxActionBottomSheetDialog
import com.duckduckgo.common.ui.compose.sheets.DaxPromoBottomSheetDialog
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.PromoBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import kotlinx.collections.immutable.toImmutableList
import com.duckduckgo.app.browser.R as AppR
import com.duckduckgo.mobile.android.R as DesignSystemR

internal val dialogQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.TextDialog to dialogRenderer(
        label = "Text Alert Dialog",
        show = { context -> textDialog(context).show() },
        dialog = { onDismiss -> TextDialogContent(onDismiss) },
    ),
    QuestionId.SingleChoiceDialog to dialogRenderer(
        label = "Single Choice Alert Dialog",
        show = { context -> radioDialog(context).show() },
        dialog = { onDismiss -> SingleChoiceDialogContent(onDismiss) },
    ),
    QuestionId.StackedDialog to dialogRenderer(
        label = "Stacked Alert Dialog",
        show = { context -> stackedDialog(context, buttonCount = 4).show() },
        dialog = { onDismiss -> StackedDialogContent(onDismiss, buttonCount = 4) },
    ),
    QuestionId.ActionBottomSheetWithTitle to dialogRenderer(
        label = "Action Bottom Sheet With Title And Icons",
        show = { context ->
            ActionBottomSheetDialog.Builder(context)
                .setTitle("Title")
                .setPrimaryItem("Primary Item", DesignSystemR.drawable.ic_add_16)
                .setSecondaryItem("Secondary Item", DesignSystemR.drawable.ic_add_16)
                .show()
        },
        dialog = { onDismiss ->
            ActionSheetContent(onDismiss)
        },
    ),
    QuestionId.PromoBottomSheetWithImage to dialogRenderer(
        label = "Promo Bottom Sheet With Image",
        show = { context ->
            promoSheet(context).setIcon(DesignSystemR.drawable.ic_bottom_sheet_promo_icon).setTitle("Title").show()
        },
        dialog = { onDismiss -> PromoSheetContent(onDismiss) },
    ),
)

private const val PROMO_CONTENT = "Add our search widget to your home screen for quick, easy access."

private fun dialogRenderer(
    label: String,
    show: (Context) -> Unit,
    dialog: @Composable (onDismiss: () -> Unit) -> Unit,
): QuizRenderer = quizRenderer(
    createView = { context ->
        LayoutInflater.from(context).inflate(AppR.layout.quiz_view_dialog_trigger, null, false).apply {
            (this as DaxButtonSecondary).text = label
            setOnClickListener { show(context) }
        }
    },
    composeSample = {
        var showDialog by remember { mutableStateOf(false) }
        DaxSecondaryButton(
            text = label,
            onClick = { showDialog = true },
            size = DaxButtonSize.Large,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showDialog) {
            dialog { showDialog = false }
        }
    },
)

private fun textDialog(context: Context) = TextAlertDialogBuilder(context)
    .setTitle(DesignSystemR.string.text_dialog_title)
    .setMessage(DesignSystemR.string.text_dialog_message)
    .setPositiveButton(DesignSystemR.string.text_dialog_positive)
    .setNegativeButton(DesignSystemR.string.text_dialog_negative)

private fun radioDialog(context: Context) = RadioListAlertDialogBuilder(context)
    .setTitle(DesignSystemR.string.text_dialog_title)
    .setMessage(DesignSystemR.string.text_dialog_message)
    .setPositiveButton(DesignSystemR.string.text_dialog_positive)
    .setNegativeButton(DesignSystemR.string.text_dialog_negative)
    .setOptions(List(3) { DesignSystemR.string.text_dialog_option })

private fun stackedDialog(context: Context, buttonCount: Int) = StackedAlertDialogBuilder(context)
    .setTitle(DesignSystemR.string.text_dialog_title)
    .setMessage(DesignSystemR.string.text_dialog_message)
    .setStackedButtons(List(buttonCount) { DesignSystemR.string.text_dialog_positive })

private fun promoSheet(context: Context) = PromoBottomSheetDialog.Builder(context)
    .setContent(PROMO_CONTENT)
    .setPrimaryButton("Button")
    .setSecondaryButton("Button")

@Composable
private fun TextDialogContent(onDismiss: () -> Unit) {
    DaxTextAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(DesignSystemR.string.text_dialog_title),
        message = stringResource(DesignSystemR.string.text_dialog_message),
        positiveButtonText = stringResource(DesignSystemR.string.text_dialog_positive),
        onPositiveClick = {},
        negativeButtonText = stringResource(DesignSystemR.string.text_dialog_negative),
        onNegativeClick = {},
    )
}

@Composable
private fun SingleChoiceDialogContent(onDismiss: () -> Unit) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    val optionText = stringResource(DesignSystemR.string.text_dialog_option)
    val options = List(3) { index -> DaxRadioOption(text = optionText, onSelected = { selectedIndex = index }) }.toImmutableList()
    DaxAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(DesignSystemR.string.text_dialog_title),
        message = { DaxText(text = stringResource(DesignSystemR.string.text_dialog_message)) },
        content = { DaxRadioOptions(options = options, selectedIndex = selectedIndex) },
        buttons = {
            DaxGhostButton(text = stringResource(DesignSystemR.string.text_dialog_negative), onClick = onDismiss)
            DaxPrimaryButton(text = stringResource(DesignSystemR.string.text_dialog_positive), enabled = selectedIndex >= 0, onClick = onDismiss)
        },
    )
}

@Composable
private fun StackedDialogContent(onDismiss: () -> Unit, buttonCount: Int) {
    val positive = stringResource(DesignSystemR.string.text_dialog_positive)
    val buttons = List(buttonCount) { DaxStackedButton(text = positive, onClick = onDismiss) }.toImmutableList()
    DaxAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(DesignSystemR.string.text_dialog_title),
        message = { DaxText(text = stringResource(DesignSystemR.string.text_dialog_message)) },
        buttons = { DaxPrimaryStackedButtons(buttons = buttons) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheetContent(onDismiss: () -> Unit) {
    DaxActionBottomSheetDialog(
        title = "Title",
        sheetState = rememberModalBottomSheetState(),
        onDismissRequest = onDismiss,
        content = {
            listOf("Primary Item", "Secondary Item").forEach { text ->
                item {
                    DaxOneLineListItem(
                        primaryText = text,
                        leadingContent = { Icon(painterResource(DesignSystemR.drawable.ic_add_24), contentDescription = null) },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromoSheetContent(onDismiss: () -> Unit) {
    DaxPromoBottomSheetDialog(
        title = "Title",
        description = PROMO_CONTENT,
        promoIcon = painterResource(DesignSystemR.drawable.ic_bottom_sheet_promo_icon),
        onDismissRequest = onDismiss,
        buttons = {
            DaxGhostButton(text = "Button", onClick = onDismiss)
            DaxPrimaryButton(text = "Button", onClick = onDismiss)
        },
    )
}
