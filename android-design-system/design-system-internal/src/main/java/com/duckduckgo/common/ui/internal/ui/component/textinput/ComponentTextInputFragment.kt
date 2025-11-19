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

package com.duckduckgo.common.ui.internal.ui.component.textinput

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.text.DaxTextField
import com.duckduckgo.common.ui.compose.text.DaxTextFieldType
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.internal.databinding.ComponentTextInputViewBinding
import com.duckduckgo.common.ui.internal.ui.appComponentsViewModel
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.common.ui.view.text.TextInput.Action
import com.duckduckgo.mobile.android.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.duckduckgo.common.ui.DuckDuckGoTheme as AppTheme

@SuppressLint("NoFragment") // we don't use DI here
class ComponentTextInputFragment : Fragment() {

    private val appComponentsViewModel by appComponentsViewModel()
    private lateinit var binding: ComponentTextInputViewBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = ComponentTextInputViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.outlinedinputtext4.apply {
            setEndIcon(R.drawable.ic_copy_24)
            onAction { toastOnClick(it) }
        }
        binding.outlinedinputtext6.onAction { toastOnClick(it) }
        binding.outlinedinputtext8.onAction { toastOnClick(it) }
        binding.outlinedinputtext20.onAction { toastOnClick(it) }
        binding.outlinedinputtext30.onAction { toastOnClick(it) }
        binding.outlinedinputtext31.onAction { toastOnClick(it) }
        binding.outlinedinputtext32.onAction { toastOnClick(it) }
        binding.outlinedinputtext33.onAction { toastOnClick(it) }
        binding.outlinedinputtext21.error = "This is an error"

        val isDarkTheme = runBlocking { appComponentsViewModel.themeFlow.first() } == AppTheme.DARK
        setupComposeViews(view, isDarkTheme)
    }

    private fun toastOnClick(action: Action) = when (action) {
        is Action.PerformEndAction -> {
            Snackbar.make(binding.root, "Element clicked", Snackbar.LENGTH_SHORT).show()
        }
    }

    @Suppress("LongMethod")
    private fun setupComposeViews(view: View, isDarkTheme: Boolean) {
        // Hint text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_1, isDarkTheme = isDarkTheme) {
            var text by remember { mutableStateOf("") }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Hint text",
            )
        }

        // Single line editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_3, isDarkTheme = isDarkTheme) {
            var text by remember {
                mutableStateOf("This is an editable text! It has a very long text to show how it behaves when the text is too long to fit in a single line.\n\nIt is restricted to a single line.")
            }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Single line editable text",
                inputType = DaxTextFieldType.SingleLine,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Multi line editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_2, isDarkTheme = isDarkTheme) {
            var text by remember {
                mutableStateOf("This is an editable text! It has a very long text to show how it behaves when the text is too long to fit in a single line.\n\nIt can include multiline text.")
            }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Multi line editable text",
                inputType = DaxTextFieldType.MultiLine,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Form mode editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_40, isDarkTheme = isDarkTheme) {
            var text by remember {
                mutableStateOf("This is an editable text! It has a very long text to show how it behaves when the text is too long to fit in a single line.\n\nIt can include multiline text. Form mode is 3 lines minimum")
            }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Form mode editable text",
                inputType = DaxTextFieldType.FormMode,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text full click listener with end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_30, isDarkTheme = isDarkTheme) {
            val text = remember { "Non-editable text full click listener with end icon." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text full click listener with end icon",
                inputType = DaxTextFieldType.SingleLine,
                clickable = true,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text full click listener
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_31, isDarkTheme = isDarkTheme) {
            val text = remember { "Non-editable text full click listener." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text full click listener",
                inputType = DaxTextFieldType.SingleLine,
                clickable = true,
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text with line truncation and end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_32, isDarkTheme = isDarkTheme) {
            val text = remember { "Non-editable text with line truncation and end icon. It has a very long text to show how it behaves when the text is too long to fit in a single line." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text with line truncation and end icon",
                isEditable = false,
                inputType = DaxTextFieldType.SingleLine,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text with line truncation
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_33, isDarkTheme = isDarkTheme) {
            val text = remember { "Non-editable text with line truncation. It has a very long text to show how it behaves when the text is too long to fit in a single line." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text with line truncation",
                isEditable = false,
                inputType = DaxTextFieldType.SingleLine,
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text with end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_4, isDarkTheme = isDarkTheme) {
            val text = remember { "This is not editable." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text with end icon",
                isEditable = false,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable text without end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_5, isDarkTheme = isDarkTheme) {
            val text = remember { "This is not editable and has no icon. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable text without end icon",
                isEditable = false,
            )
        }

        // Editable password that fits in one line
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_6, isDarkTheme = isDarkTheme) {
            var text by remember { mutableStateOf("Loremipsumolor") }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Editable password that fits in one line",
                inputType = DaxTextFieldType.Password,
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Editable password that doesn't fit in one line
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_9, isDarkTheme = isDarkTheme) {
            var text by remember { mutableStateOf("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore.") }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Editable password that doesn't fit in one line",
                inputType = DaxTextFieldType.Password,
            )
        }

        // Non-editable password
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_8, isDarkTheme = isDarkTheme) {
            val text = remember { "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable password",
                isEditable = false,
                inputType = DaxTextFieldType.Password,
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Non-editable password with icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_20, isDarkTheme = isDarkTheme) {
            val text = remember { "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore." }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Non-editable password with icon",
                isEditable = false,
                inputType = DaxTextFieldType.Password,
                endIcon = R.drawable.ic_copy_24,
                endIconContentDescription = "Copy",
                onEndIconClick = { toastOnClick(Action.PerformEndAction) },
            )
        }

        // Error
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_21, isDarkTheme = isDarkTheme) {
            var text by remember { mutableStateOf("This is an error") }
            DaxTextField(
                value = text,
                onValueChange = { text = it },
                hint = "Error",
                error = "This is an error",
            )
        }

        // Disabled text input
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_22, isDarkTheme = isDarkTheme) {
            val text = remember { "This input is disabled" }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Disabled text input",
                enabled = false,
            )
        }

        // Disabled multi line input
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_23, isDarkTheme = isDarkTheme) {
            val text = remember { "This input is disabled" }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Disabled multi line input",
                enabled = false,
                inputType = DaxTextFieldType.MultiLine,
            )
        }

        // Disabled password
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_24, isDarkTheme = isDarkTheme) {
            val text = remember { "This password input is disabled" }
            DaxTextField(
                value = text,
                onValueChange = {},
                hint = "Disabled password",
                enabled = false,
                inputType = DaxTextFieldType.Password,
            )
        }
    }
}
