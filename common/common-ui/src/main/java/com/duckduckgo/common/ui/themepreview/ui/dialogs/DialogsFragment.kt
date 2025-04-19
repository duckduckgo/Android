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

package com.duckduckgo.common.ui.themepreview.ui.dialogs

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.duckduckgo.common.ui.view.LottieDaxDialog
import com.duckduckgo.common.ui.view.TypewriterDaxDialog
import com.duckduckgo.common.ui.view.button.ButtonType.DESTRUCTIVE
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST
import com.duckduckgo.common.ui.view.button.ButtonType.GHOST_ALT
import com.duckduckgo.common.ui.view.dialog.ActionBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.PromoBottomSheetDialog
import com.duckduckgo.common.ui.view.dialog.RadioListAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.mobile.android.R
import com.google.android.material.snackbar.Snackbar

/** Fragment to display a list of dialogs. */
@SuppressLint("NoFragment") // we don't use DI here
class DialogsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_components_dialogs, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.textAlertDialogWithImageButton)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setHeaderImageResource(R.drawable.ic_dax_icon)
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .setNegativeButton(R.string.text_dialog_negative)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.radioButtonAlertDialog)?.let {
            it.setOnClickListener {
                RadioListAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .setNegativeButton(R.string.text_dialog_negative)
                    .setOptions(listOf(R.string.text_dialog_option, R.string.text_dialog_option, R.string.text_dialog_option))
                    .addEventListener(
                        object : RadioListAlertDialogBuilder.EventListener() {
                            override fun onRadioItemSelected(selectedItem: Int) {
                                Snackbar.make(it, "Radio Button $selectedItem selected", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.radioButtonDestructiveAlertDialog)?.let {
            it.setOnClickListener {
                RadioListAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive, DESTRUCTIVE)
                    .setNegativeButton(R.string.text_dialog_negative, GHOST_ALT)
                    .setOptions(listOf(R.string.text_dialog_option, R.string.text_dialog_option, R.string.text_dialog_option))
                    .addEventListener(
                        object : RadioListAlertDialogBuilder.EventListener() {
                            override fun onRadioItemSelected(selectedItem: Int) {
                                Snackbar.make(it, "Radio Button $selectedItem selected", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertDialogButton)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .setNegativeButton(R.string.text_dialog_negative)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertDestructiveDialogButton)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive, DESTRUCTIVE)
                    .setNegativeButton(R.string.text_dialog_negative, GHOST_ALT)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertSingleDialogButton)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive, GHOST)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertDialogCancellable)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setCancellable(true)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .setNegativeButton(R.string.text_dialog_negative)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onDialogCancelled() {
                                Snackbar.make(it, "Dialog cancelled", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertDialogOneButton)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setCancellable(true)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.textAlertDialogCheckbox)?.let {
            it.setOnClickListener {
                TextAlertDialogBuilder(requireContext())
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setPositiveButton(R.string.text_dialog_positive)
                    .setNegativeButton(R.string.text_dialog_negative)
                    .setCheckBoxText(R.string.text_dialog_checkbox)
                    .addEventListener(
                        object : TextAlertDialogBuilder.EventListener() {
                            var isChecked: Boolean = false
                            override fun onPositiveButtonClicked() {
                                Snackbar.make(it, "Positive Button Clicked, Checked $isChecked ", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onNegativeButtonClicked() {
                                Snackbar.make(it, "Negative Button Clicked, Checked $isChecked", Snackbar.LENGTH_SHORT).show()
                            }

                            override fun onCheckedChanged(checked: Boolean) {
                                isChecked = checked
                            }
                        },
                    )
                    .show()
            }
        }

        view.findViewById<Button>(R.id.stackedAlertDialogWithImageButton)?.let {
            it.setOnClickListener {
                StackedAlertDialogBuilder(requireContext())
                    .setHeaderImageResource(R.drawable.ic_dax_icon)
                    .setTitle(R.string.text_dialog_title)
                    .setMessage(R.string.text_dialog_message)
                    .setStackedButtons(
                        listOf(
                            R.string.text_dialog_positive,
                            R.string.text_dialog_positive,
                            R.string.text_dialog_positive,
                        ),
                    )
                    .addEventListener(
                        object : StackedAlertDialogBuilder.EventListener() {
                            override fun onButtonClicked(position: Int) {
                                Snackbar.make(it, "Button $position Clicked", Snackbar.LENGTH_SHORT).show()
                            }
                        },
                    )
                    .show()
            }

            view.findViewById<Button>(R.id.stackedAlertDialogWithButtons)?.let {
                it.setOnClickListener {
                    StackedAlertDialogBuilder(requireContext())
                        .setTitle(R.string.text_dialog_title)
                        .setMessage(R.string.text_dialog_message)
                        .setStackedButtons(
                            listOf(
                                R.string.text_dialog_positive,
                                R.string.text_dialog_positive,
                                R.string.text_dialog_positive,
                                R.string.text_dialog_positive,
                            ),
                        )
                        .addEventListener(
                            object : StackedAlertDialogBuilder.EventListener() {
                                override fun onButtonClicked(position: Int) {
                                    Snackbar.make(it, "Button $position Clicked", Snackbar.LENGTH_SHORT).show()
                                }
                            },
                        )
                        .show()
                }

                view.findViewById<Button>(R.id.stackedAlertDestructiveDialogWithButtons)?.let {
                    it.setOnClickListener {
                        StackedAlertDialogBuilder(requireContext())
                            .setTitle(R.string.text_dialog_title)
                            .setMessage(R.string.text_dialog_message)
                            .setStackedButtons(
                                listOf(
                                    R.string.text_dialog_positive,
                                    R.string.text_dialog_positive,
                                    R.string.text_dialog_positive,
                                    R.string.text_dialog_positive,
                                ),
                            )
                            .setDestructiveButtons(true)
                            .addEventListener(
                                object : StackedAlertDialogBuilder.EventListener() {
                                    override fun onButtonClicked(position: Int) {
                                        Snackbar.make(it, "Button $position Clicked", Snackbar.LENGTH_SHORT).show()
                                    }
                                },
                            )
                            .show()
                    }

                    view.findViewById<Button>(R.id.actionBottomSheetButton)?.let { button ->
                        button.setOnClickListener {
                            ActionBottomSheetDialog.Builder(requireContext())
                                .setPrimaryItem("Primary Item")
                                .setSecondaryItem("Secondary Item")
                                .addEventListener(
                                    object : ActionBottomSheetDialog.EventListener() {
                                        override fun onPrimaryItemClicked() {
                                            Toast.makeText(context, "Primary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onSecondaryItemClicked() {
                                            Toast.makeText(context, "Secondary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                .show()
                        }
                    }

                    view.findViewById<Button>(R.id.actionBottomSheetButtonWithTitle)?.let { button ->
                        button.setOnClickListener {
                            ActionBottomSheetDialog.Builder(requireContext())
                                .setTitle("Title")
                                .setPrimaryItem("Primary Item", R.drawable.ic_add_16)
                                .setSecondaryItem("Secondary Item", R.drawable.ic_add_16)
                                .addEventListener(
                                    object : ActionBottomSheetDialog.EventListener() {
                                        override fun onPrimaryItemClicked() {
                                            Toast.makeText(context, "Primary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onSecondaryItemClicked() {
                                            Toast.makeText(context, "Secondary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                .show()
                        }
                    }

                    view.findViewById<Button>(R.id.promoBottomSheetButton)?.let { button ->
                        button.setOnClickListener {
                            PromoBottomSheetDialog.Builder(requireContext())
                                .setContent("Add our search widget to your home screen for quick, easy access.")
                                .setPrimaryButton("Button")
                                .setSecondaryButton("Button")
                                .addEventListener(
                                    object : PromoBottomSheetDialog.EventListener() {
                                        override fun onPrimaryButtonClicked() {
                                            super.onPrimaryButtonClicked()
                                            Toast.makeText(context, "Primary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onSecondaryButtonClicked() {
                                            super.onSecondaryButtonClicked()
                                            Toast.makeText(context, "Secondary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                .show()
                        }
                    }

                    view.findViewById<Button>(R.id.promoBottomSheetButtonWithTitle)?.let { button ->
                        button.setOnClickListener {
                            PromoBottomSheetDialog.Builder(requireContext())
                                .setTitle("Title")
                                .setContent("Add our search widget to your home screen for quick, easy access.")
                                .setPrimaryButton("Button")
                                .setSecondaryButton("Button")
                                .addEventListener(
                                    object : PromoBottomSheetDialog.EventListener() {
                                        override fun onPrimaryButtonClicked() {
                                            super.onPrimaryButtonClicked()
                                            Toast.makeText(context, "Primary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onSecondaryButtonClicked() {
                                            super.onSecondaryButtonClicked()
                                            Toast.makeText(context, "Secondary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                .show()
                        }
                    }

                    view.findViewById<Button>(R.id.promoBottomSheetButtonWithImage)?.let { button ->
                        button.setOnClickListener {
                            PromoBottomSheetDialog.Builder(requireContext())
                                .setIcon(R.drawable.ic_bottom_sheet_promo_icon)
                                .setTitle("Title")
                                .setContent("Add our search widget to your home screen for quick, easy access.")
                                .setPrimaryButton("Button")
                                .setSecondaryButton("Button")
                                .addEventListener(
                                    object : PromoBottomSheetDialog.EventListener() {
                                        override fun onPrimaryButtonClicked() {
                                            super.onPrimaryButtonClicked()
                                            Toast.makeText(context, "Primary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onSecondaryButtonClicked() {
                                            super.onSecondaryButtonClicked()
                                            Toast.makeText(context, "Secondary Item Clicked", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                .show()
                        }
                    }

                    view.findViewById<Button>(R.id.animated_button)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                TypewriterDaxDialog.newInstance(
                                    daxText = "This is an example of a Dax dialog with an animated text",
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    toolbarDimmed = true,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }

                    view.findViewById<Button>(R.id.not_dimmed_button)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                TypewriterDaxDialog.newInstance(
                                    daxText = "This is an example of a Dax dialog with toolbar location not dimmed",
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    toolbarDimmed = false,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }

                    view.findViewById<Button>(R.id.dismissible_button)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                TypewriterDaxDialog.newInstance(
                                    daxText = "This is an example of a Dax dialog that can be dimissed by clicking anywhere in the screen.",
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    toolbarDimmed = true,
                                    dismissible = true,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }

                    view.findViewById<Button>(R.id.no_hide_button)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                TypewriterDaxDialog.newInstance(
                                    daxText = "This is an example of a Dax dialog without hide button.",
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    toolbarDimmed = true,
                                    showHideButton = false,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }

                    view.findViewById<Button>(R.id.custom_typing_button)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                TypewriterDaxDialog.newInstance(
                                    daxText = "This is an example of a Dax dialog with a custom typing delay of 200ms.",
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    toolbarDimmed = true,
                                    typingDelayInMs = 200L,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }

                    view.findViewById<Button>(R.id.cookie_content)?.let {
                        it.setOnClickListener {
                            activity?.supportFragmentManager?.let { fragmentManager ->
                                LottieDaxDialog.newInstance(
                                    titleText = "Cookie Prompt",
                                    descriptionText = "This is an example of a Dax dialog with a custom animation",
                                    lottieRes = R.raw.cookie_banner_dark,
                                    primaryButtonText = "Primary CTA",
                                    secondaryButtonText = "Secondary CTA",
                                    hideButtonText = "Hide",
                                    showHideButton = false,
                                ).show(fragmentManager, "dialog")
                            }
                        }
                    }
                }
            }
        }
    }
}
