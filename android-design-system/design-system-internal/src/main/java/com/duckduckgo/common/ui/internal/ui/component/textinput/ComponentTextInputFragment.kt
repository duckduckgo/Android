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
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.compose.text.DaxText
import com.duckduckgo.common.ui.compose.textfield.DaxSecureTextField
import com.duckduckgo.common.ui.compose.textfield.DaxTextField
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldDefaults
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldInputMode
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldLineLimits
import com.duckduckgo.common.ui.compose.textfield.DaxTextFieldTrailingIconScope
import com.duckduckgo.common.ui.internal.databinding.ComponentTextInputViewBinding
import com.duckduckgo.common.ui.internal.ui.appComponentsViewModel
import com.duckduckgo.common.ui.internal.ui.setupThemedComposeView
import com.duckduckgo.common.ui.view.text.TextInput.Action
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.mobile.android.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.duckduckgo.common.ui.DuckDuckGoTheme as AppTheme

@SuppressLint("NoFragment") // we don't use DI here
class ComponentTextInputFragment : Fragment() {

    private val appComponentsViewModel by appComponentsViewModel()
    private lateinit var binding: ComponentTextInputViewBinding

    private var textChangedWatcher: TextChangedWatcher? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = ComponentTextInputViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("DenyListedApi")
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
        binding.outlinedinputtext41.onAction { toastOnClick(it) }
        binding.outlinedinputtext21.error = "This is an error"
        binding.outlinedinputtext41.error = "This is an error"

        textChangedWatcher = object : TextChangedWatcher() {
            override fun afterTextChanged(editable: Editable) {
                binding.outlinedinputtext27.error = if (editable.toString() != "Compose") {
                    "Text must be 'Compose'"
                } else {
                    null
                }
            }
        }.also {
            binding.outlinedinputtext27.addTextChangedListener(it)
        }
        binding.outlinedinputtext28.setSelectAllOnFocus(true)

        binding.button1.setOnClickListener {
            binding.outlinedinputtext29.requestFocus()
        }

        val isDarkTheme = runBlocking { appComponentsViewModel.themeFlow.first() } == AppTheme.DARK
        setupComposeViews(view, isDarkTheme)
    }

    override fun onDestroyView() {
        textChangedWatcher?.let {
            binding.outlinedinputtext27.removeTextChangedListener(it)
        }
        textChangedWatcher = null
        super.onDestroyView()
    }

    private fun toastOnClick(action: Action) = when (action) {
        is Action.PerformEndAction -> {
            Snackbar.make(binding.root, "Element clicked", Snackbar.LENGTH_SHORT).show()
        }
    }

    @Suppress("LongMethod")
    private fun setupComposeViews(
        view: View,
        isDarkTheme: Boolean,
    ) {
        // Hint text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_1, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState()
            DaxTextField(
                state = state,
                label = "Hint text",
            )
        }

        // Single line editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_3, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "This is an editable text! It has a very long text to show how it behaves when " +
                        "the text is too long to fit in a single line.\n\nIt is restricted to a single line.",
                )
            DaxTextField(
                state = state,
                label = "Single line editable text",
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Multi line editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_2, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "This is an editable text! It has a very long text to show how it behaves when " +
                        "the text is too long to fit in a single line.\n\nIt can include multiline text.",
                )
            DaxTextField(
                state = state,
                label = "Multi line editable text",
                lineLimits = DaxTextFieldLineLimits.MultiLine,
            )
        }

        // Form mode editable text
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_40, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "This is an editable text! It has a very long text to show how it behaves when " +
                        "the text is too long to fit in a single line.\n\nIt can include multiline text. Form mode is 3 lines minimum",
                )
            DaxTextField(
                state = state,
                label = "Form mode editable text",
                lineLimits = DaxTextFieldLineLimits.Form,
            )
        }

        // Non-editable text full click listener with end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_30, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("Non-editable text full click listener with end icon.")

            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()

            LaunchedEffect(pressed) {
                if (pressed) {
                    toastOnClick(Action.PerformEndAction)
                }
            }

            DaxTextField(
                state = state,
                label = "Non-editable text full click listener with end icon",
                trailingIcon = {
                    DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = "Copy",
                        onClick = {
                            toastOnClick(Action.PerformEndAction)
                        },
                    )
                },
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
                interactionSource = interactionSource,
            )
        }

        // Non-editable text full click listener
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_31, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("Non-editable text full click listener.")

            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()

            LaunchedEffect(pressed) {
                if (pressed) {
                    toastOnClick(Action.PerformEndAction)
                }
            }

            DaxTextField(
                state = state,
                label = "Non-editable text full click listener",
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
                interactionSource = interactionSource,
            )
        }

        // Non-editable text with line truncation and end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_32, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "Non-editable text with line truncation and end icon. It has a very long text to " +
                        "show how it behaves when the text is too long to fit in a single line.",
                )
            DaxTextField(
                state = state,
                label = "Non-editable text with line truncation and end icon",
                trailingIcon = {
                    DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = "Copy",
                        onClick = {
                            toastOnClick(Action.PerformEndAction)
                        },
                    )
                },
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Non-editable text with line truncation
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_33, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "Non-editable text with line truncation. It has a very long text to show how it " +
                        "behaves when the text is too long to fit in a single line.",
                )

            DaxTextField(
                state = state,
                label = "Non-editable text with line truncation",
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Non-editable text with end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_4, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("This is not editable.")
            DaxTextField(
                state = state,
                label = "Non-editable text with end icon",
                trailingIcon = {
                    DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = "Copy",
                        onClick = {
                            toastOnClick(Action.PerformEndAction)
                        },
                    )
                },
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Non-editable text without end icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_5, isDarkTheme = isDarkTheme) {
            val state =
                rememberTextFieldState(
                    "This is not editable and has no icon. Lorem ipsum dolor sit amet, consectetur " +
                        "adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                )
            DaxTextField(
                state = state,
                label = "Non-editable text without end icon",
                inputMode = DaxTextFieldInputMode.ReadOnly,
            )
        }

        // Editable password that fits in one line
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_6, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("Loremipsumolor")

            DaxSecureTextField(
                state = state,
                label = "Editable password that fits in one line",
            )
        }

        // Editable password that doesn't fit in one line
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_9, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
                    "eiusmod tempor incididunt ut labore.",
            )

            DaxSecureTextField(
                state = state,
                label = "Editable password that doesn't fit in one line",
            )
        }

        // Non-editable password
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_8, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
                    "eiusmod tempor incididunt ut labore.",
            )

            DaxSecureTextField(
                state = state,
                label = "Non-editable password",
                inputMode = DaxTextFieldInputMode.ReadOnly,
            )
        }

        // Non-editable password with icon
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_20, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState(
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do " +
                    "eiusmod tempor incididunt ut labore.",
            )

            DaxSecureTextField(
                state = state,
                label = "Non-editable password with icon",
                trailingIcon = {
                    DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = "Copy",
                        onClick = {
                            toastOnClick(Action.PerformEndAction)
                        },
                    )
                },
                inputMode = DaxTextFieldInputMode.ReadOnly,
            )
        }

        // Error
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_21, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("This is an error")
            DaxTextField(
                state = state,
                label = "Error",
                error = "This is an error",
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Non-editable text with end icon in error state
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_41, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("Non-editable text with end icon in error state")

            DaxTextField(
                state = state,
                label = "Non-editable text full click listener with end icon",
                error = "This is an error",
                trailingIcon = {
                    DaxTextFieldTrailingIconScope.DaxTextFieldTrailingIcon(
                        painter = painterResource(R.drawable.ic_copy_24),
                        contentDescription = "Copy",
                        onClick = {
                            toastOnClick(Action.PerformEndAction)
                        },
                    )
                },
                inputMode = DaxTextFieldInputMode.ReadOnly,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Disabled text input
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_22, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("This input is disabled")
            DaxTextField(
                state = state,
                label = "Disabled text input",
                inputMode = DaxTextFieldInputMode.Disabled,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Disabled multi line input
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_23, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("This input is disabled")
            DaxTextField(
                state = state,
                label = "Disabled multi line input",
                inputMode = DaxTextFieldInputMode.Disabled,
                lineLimits = DaxTextFieldLineLimits.MultiLine,
            )
        }

        // Disabled password
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_24, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("This password input is disabled")

            DaxSecureTextField(
                state = state,
                label = "Disabled password",
                inputMode = DaxTextFieldInputMode.Disabled,
            )
        }

        // IP Address
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_25, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("192.168.1.1")
            DaxTextField(
                state = state,
                label = "IP Address",
                keyboardOptions = DaxTextFieldDefaults.IpAddressKeyboardOptions,
                inputTransformation = DaxTextFieldDefaults.IpAddressInputTransformation(),
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // URL
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_26, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("https://www.duckduckgo.com")
            DaxTextField(
                state = state,
                label = "URL",
                keyboardOptions = DaxTextFieldDefaults.UrlKeyboardOptions,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Observable text - option 1
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_27a, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("")

            DaxTextField(
                state = state,
                label = "Observable text - option 1",
                error = if (state.text.isNotEmpty() && state.text != "Compose") {
                    "Text must be 'Compose'"
                } else {
                    null
                },
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Observable text - option 2
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_27b, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("")
            var error by remember { mutableStateOf<String?>(null) }

            // provides a way to observe text changes as recommended by
            // https://developer.android.com/develop/ui/compose/text/migrate-state-based#conforming-approach
            LaunchedEffect(state) {
                snapshotFlow { state.text.toString() }.collectLatest {
                    // can call a view model function here with the new text value
                    // for demo purposes, we'll show an error
                    error = if (it.isNotEmpty() && it != "Compose") {
                        "Text must be 'Compose'"
                    } else {
                        null
                    }
                }
            }

            DaxTextField(
                state = state,
                label = "Observable text - option 2",
                error = error,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Autoselect text on focus
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_28, isDarkTheme = isDarkTheme) {
            val state = rememberTextFieldState("Tap to focus and select all text")

            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            LaunchedEffect(isFocused) {
                if (isFocused) {
                    state.edit {
                        selectAll()
                    }
                }
            }

            DaxTextField(
                state = state,
                label = "Autoselect text on focus",
                interactionSource = interactionSource,
                lineLimits = DaxTextFieldLineLimits.SingleLine,
            )
        }

        // Focus programmatically
        view.setupThemedComposeView(id = com.duckduckgo.common.ui.internal.R.id.compose_text_input_29, isDarkTheme = isDarkTheme) {
            val focusRequester = remember { FocusRequester() }
            val state = rememberTextFieldState("")

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { focusRequester.requestFocus() }) {
                    DaxText(text = "Click to focus the input below")
                }

                DaxTextField(
                    state = state,
                    label = "Programmatically focusable text input",
                    modifier = Modifier.focusRequester(focusRequester),
                    lineLimits = DaxTextFieldLineLimits.SingleLine,
                )
            }
        }
    }
}
