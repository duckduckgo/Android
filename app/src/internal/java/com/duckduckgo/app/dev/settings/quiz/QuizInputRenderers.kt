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
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.duckduckgo.common.ui.compose.button.DaxIconButton
import com.duckduckgo.common.ui.compose.checkbox.DaxCheckbox
import com.duckduckgo.common.ui.compose.radiobutton.DaxRadioButton
import com.duckduckgo.common.ui.compose.slider.DaxSlider
import com.duckduckgo.common.ui.compose.switch.DaxSwitch
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.textfield.DaxTextField
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldInputMode
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldLineLimits
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope
import com.duckduckgo.common.ui.view.button.IconButton
import com.duckduckgo.common.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.R
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.duckduckgo.app.browser.R as AppR

internal val inputQuizRenderers: Map<QuestionId, QuizRenderer> = mapOf(
    QuestionId.IconButton to quizRenderer(
        createView = { context ->
            startAligned(
                IconButton(context).apply {
                    contentDescription = "Menu"
                },
            )
        },
        composeSample = {
            DaxIconButton(
                onClick = {},
                iconPainter = painterResource(R.drawable.ic_union),
                contentDescription = "Menu",
                modifier = Modifier.size(ICON_BUTTON_WIDTH_DP.dp, ICON_BUTTON_HEIGHT_DP.dp),
            )
        },
    ),
    QuestionId.Switch to quizRenderer(
        createView = { context ->
            startAligned(LayoutInflater.from(context).inflate(AppR.layout.quiz_view_switch, null, false))
        },
        composeSample = {
            var checked by remember { mutableStateOf(true) }
            DaxSwitch(
                checked = checked,
                onCheckedChange = { checked = it },
            )
        },
    ),
    QuestionId.RadioButton to quizRenderer(
        createView = ::radioButtonSample,
        composeSample = {
            var selectedIndex by remember { mutableIntStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DaxRadioButton(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                )
                DaxText("Option A")
                DaxRadioButton(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                )
                DaxText("Option B")
            }
        },
    ),
    QuestionId.Checkbox to quizRenderer(
        createView = ::checkboxSample,
        composeSample = {
            var checked by remember { mutableStateOf(true) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DaxCheckbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
                DaxText("Receive updates")
            }
        },
    ),
    QuestionId.Slider to quizRenderer(
        createView = { context ->
            Slider(context).apply {
                value = SLIDER_INITIAL_VALUE
                contentDescription = "Volume"
                labelBehavior = LabelFormatter.LABEL_GONE
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
        },
        composeSample = {
            var value by remember { mutableFloatStateOf(SLIDER_INITIAL_VALUE) }
            DaxSlider(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ),
    QuestionId.TextField to quizRenderer(
        createView = ::textFieldSample,
        composeSample = {
            TextFieldComposeSample()
        },
    ),
)

private const val SLIDER_INITIAL_VALUE = 0.3f

// The View IconButton wraps its 21x22dp icon with 6dp padding; the Compose one would otherwise render a different size.
private const val ICON_BUTTON_WIDTH_DP = 33
private const val ICON_BUTTON_HEIGHT_DP = 34

private fun radioButtonSample(context: Context): RadioGroup = RadioGroup(context).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(
        RadioButton(context, null).apply {
            text = "Option A"
            isChecked = true
        },
    )
    addView(
        RadioButton(context, null).apply {
            text = "Option B"
        },
    )
}

private fun checkboxSample(context: Context): CheckBox = CheckBox(context).apply {
    text = "Receive updates"
    isChecked = true
}

private fun textFieldSample(context: Context): View =
    LayoutInflater.from(context).inflate(AppR.layout.quiz_view_text_fields, null, false).apply {
        findViewById<com.duckduckgo.common.ui.view.text.DaxTextInput>(AppR.id.quizTextFieldError).error =
            "Enter a valid email"
    }

@androidx.compose.runtime.Composable
private fun TextFieldComposeSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DaxTextField(
            state = rememberTextFieldState("Ada"),
            label = "Display name",
            lineLimits = DaxTextFieldLineLimits.SingleLine,
        )
        DaxTextField(
            state = rememberTextFieldState("Line one\nLine two"),
            label = "Notes",
            lineLimits = DaxTextFieldLineLimits.MultiLine,
        )
        DaxTextField(
            state = rememberTextFieldState("A short bio"),
            label = "About you",
            lineLimits = DaxTextFieldLineLimits.Form,
        )
        DaxTextField(
            state = rememberTextFieldState("not-an-email"),
            label = "Email",
            error = "Enter a valid email",
            lineLimits = DaxTextFieldLineLimits.SingleLine,
        )
        DaxTextField(
            state = rememberTextFieldState("Unavailable"),
            label = "Disabled field",
            inputMode = DaxTextFieldInputMode.Disabled,
            lineLimits = DaxTextFieldLineLimits.SingleLine,
        )
        DaxTextField(
            state = rememberTextFieldState("https://duckduckgo.com"),
            label = "Share link",
            inputMode = DaxTextFieldInputMode.ReadOnly,
            lineLimits = DaxTextFieldLineLimits.SingleLine,
            trailingIcon = {
                DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                    painter = painterResource(R.drawable.ic_copy_24),
                    contentDescription = "Copy link",
                )
            },
        )
    }
}
