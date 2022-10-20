/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.ui.view.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.duckduckgo.mobile.android.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.toDp
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RadioListAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onRadioItemSelected(selectedItem: Int) {}
        open fun onPositiveButtonClicked(selectedItem: Int) {}
        open fun onNegativeButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    class RadioListAlertDialogBuilder(val context: Context) {
        var listener: EventListener = DefaultEventListener()
            private set
        var titleText: CharSequence = ""
            private set
        var messageText: CharSequence = ""
            private set
        var positiveButtonText: CharSequence = ""
            private set
        var negativeButtonText: CharSequence = ""
            private set
        var optionList: MutableList<CharSequence> = mutableListOf()
            private set
        var selectedOption = 0
            private set

        fun setTitle(@StringRes textId: Int): RadioListAlertDialogBuilder {
            titleText = context.getText(textId)
            return this
        }

        fun setMessage(@StringRes textId: Int): RadioListAlertDialogBuilder {
            messageText = context.getText(textId)
            return this
        }

        fun setTitle(text: CharSequence): RadioListAlertDialogBuilder {
            titleText = text
            return this
        }

        fun setMessage(text: CharSequence): RadioListAlertDialogBuilder {
            messageText = text
            return this
        }

        fun setOptions(
            @StringRes stackedButtonTextId: List<Int>,
            selectedItem: Int = 0
        ): RadioListAlertDialogBuilder {
            stackedButtonTextId.forEach {
                optionList.add(context.getText(it))
            }
            selectedOption = selectedItem
            return this
        }

        fun setPositiveButton(@StringRes textId: Int): RadioListAlertDialogBuilder {
            positiveButtonText = context.getText(textId)
            return this
        }

        fun setNegativeButton(@StringRes textId: Int): RadioListAlertDialogBuilder {
            negativeButtonText = context.getText(textId)
            return this
        }

        fun addEventListener(eventListener: EventListener): RadioListAlertDialogBuilder {
            listener = eventListener
            return this
        }

        fun show() {
            val binding: DialogSingleChoiceAlertBinding = DialogSingleChoiceAlertBinding.inflate(LayoutInflater.from(context))

            val dialogBuilder = MaterialAlertDialogBuilder(context, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_Dialog)
                .setView(binding.root)
                .apply {
                    setCancelable(false)
                    setOnDismissListener { listener.onDialogDismissed() }
                }
            val dialog = dialogBuilder.create()

            setViews(binding, dialog)
            checkRequiredFieldsSet()

            dialog.show()
            listener.onDialogShown()
        }

        private fun setViews(
            binding: DialogSingleChoiceAlertBinding,
            dialog: AlertDialog
        ) {
            binding.radioListDialogTitle.text = titleText

            if (messageText.isEmpty()) {
                binding.radioListDialogMessage.gone()
            } else {
                binding.radioListDialogMessage.text = messageText
            }

            optionList.forEach {
                val radioButton = RadioButton(context, null)
                radioButton.setPadding(30.toDp(), 0, 0, 0)
                radioButton.text = it
                binding.radioListDialogRadioGroup.addView(radioButton)
            }

            with(binding.radioListDialogRadioGroup) {
                check(selectedOption)
                setOnCheckedChangeListener { group, checkedId ->
                    listener.onRadioItemSelected(checkedId)
                }
            }

            binding.radioListDialogPositiveButton.text = positiveButtonText
            binding.radioListDialogPositiveButton.setOnClickListener {
                listener.onPositiveButtonClicked(binding.radioListDialogRadioGroup.checkedRadioButtonId)
                dialog.dismiss()
            }

            binding.radioListDialogNegativeButton.text = negativeButtonText
            binding.radioListDialogNegativeButton.setOnClickListener {
                listener.onNegativeButtonClicked()
                dialog.dismiss()
            }
        }

        private fun checkRequiredFieldsSet() {
            if (positiveButtonText.isEmpty()) {
                throw Exception("RadioListAlertDialog: You must always provide a Positive Button")
            }
            if (negativeButtonText.isEmpty()) {
                throw Exception("RadioListAlertDialog: You must always provide a Negative Button")
            }
            if (titleText.isEmpty()) {
                throw Exception("RadioListAlertDialog: You must always provide a Title")
            }
            if (optionList.isEmpty()) {
                throw Exception("RadioListAlertDialog: You must always provide a list of options")
            }
        }
    }
}
