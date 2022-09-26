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

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.duckduckgo.mobile.android.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.ui.view.dialog.StackedAlertDialog.Builder
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.toDp
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RadioListAlertDialog(val builder: Builder) : DialogFragment() {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onRadioItemSelected(selectedItem: Int) {}
        open fun onPositiveButtonClicked(selectedItem: Int) {}
        open fun onNegativeButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        builder.listener.onDialogShown()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding: DialogSingleChoiceAlertBinding = DialogSingleChoiceAlertBinding.inflate(layoutInflater)

        binding.radioListDialogTitle.text = builder.titleText

        if (builder.messageText.isEmpty()) {
            binding.radioListDialogMessage.gone()
        } else {
            binding.radioListDialogMessage.text = builder.messageText
        }

        builder.optionList.forEach {
            val radioButton = RadioButton(requireContext(), null)
            radioButton.setPadding(30.toDp(), 0, 0, 0)
            radioButton.text = it
            binding.radioListDialogRadioGroup.addView(radioButton)
        }

        with(binding.radioListDialogRadioGroup) {
            check(builder.selectedOption)
            setOnCheckedChangeListener { group, checkedId ->
                builder.listener.onRadioItemSelected(checkedId)
            }
        }

        binding.radioListDialogPositiveButton.text = builder.positiveButtonText
        binding.radioListDialogPositiveButton.setOnClickListener {
            builder.listener.onPositiveButtonClicked(binding.radioListDialogRadioGroup.checkedRadioButtonId)
            dismiss()
        }

        binding.radioListDialogNegativeButton.text = builder.negativeButtonText
        binding.radioListDialogNegativeButton.setOnClickListener {
            builder.listener.onNegativeButtonClicked()
            dismiss()
        }

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_Dialog
        )
            .setView(binding.root)

        isCancelable = false

        return alertDialog.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        builder.listener.onDialogDismissed()
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG_RADIO_LIST_ALERT_DIALOG = "RadioListAlertDialog"
    }

    class Builder(val context: Context) {

        var listener: EventListener = DefaultEventListener()

        var titleText: CharSequence = ""
        var messageText: CharSequence = ""

        var positiveButtonText: CharSequence = ""
        var negativeButtonText: CharSequence = ""

        var optionList: MutableList<CharSequence> = mutableListOf()
        var selectedOption = 0

        fun setTitle(
            @StringRes textId: Int,
        ): Builder {
            titleText = context.getText(textId)
            return this
        }

        fun setMessage(
            @StringRes textId: Int,
        ): Builder {
            messageText = context.getText(textId)
            return this
        }

        fun setTitle(
            text: CharSequence,
        ): Builder {
            titleText = text
            return this
        }

        fun setMessage(
            text: CharSequence,
        ): Builder {
            messageText = text
            return this
        }

        fun setOptions(
            @StringRes stackedButtonTextId: List<Int>,
            selectedItem: Int = 0
        ): Builder {
            stackedButtonTextId.forEach {
                optionList.add(context.getText(it))
            }
            selectedOption = selectedItem
            return this
        }

        fun setPositiveButton(@StringRes textId: Int): Builder {
            positiveButtonText = context.getText(textId)
            return this
        }

        fun setNegativeButton(
            @StringRes textId: Int
        ): Builder {
            negativeButtonText = context.getText(textId)
            return this
        }

        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        fun build(): RadioListAlertDialog {
            val builder = this
            if (builder.positiveButtonText.isEmpty()) {
                throw Exception("TextAlertDialog: You must always provide a Positive Button")
            }
            if (builder.negativeButtonText.isEmpty()) {
                throw Exception("TextAlertDialog: You must always provide a Negative Button")
            }
            if (builder.titleText.isEmpty()) {
                throw Exception("TextAlertDialog: You must always provide a Title")
            }
            if (builder.optionList.isEmpty()) {
                throw Exception("TextAlertDialog: You must always provide a list of options")
            }
            return RadioListAlertDialog(this)
        }
    }
}
