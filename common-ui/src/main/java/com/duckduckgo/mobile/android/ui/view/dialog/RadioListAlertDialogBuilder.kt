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
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.duckduckgo.mobile.android.ui.view.button.RadioButton
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RadioListAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onRadioItemSelected(selectedItem: Int) {}
        open fun onPositiveButtonClicked(selectedItem: Int) {}
        open fun onNegativeButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private var dialog: AlertDialog? = null

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
    var selectedOption: Int? = null
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
        selectedItem: Int? = null,
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

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogSingleChoiceAlertBinding = DialogSingleChoiceAlertBinding.inflate(LayoutInflater.from(context))

        val dialogBuilder = MaterialAlertDialogBuilder(context, com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_Dialog)
            .setView(binding.root)
            .apply {
                setCancelable(false)
                setOnDismissListener { listener.onDialogDismissed() }
            }

        dialog = dialogBuilder.create()
        setViews(binding, dialog!!)

        return this
    }

    override fun show() {
        if (dialog == null) {
            build()
        }
        dialog?.show()
        listener.onDialogShown()
    }

    override fun dismiss() {
        dialog?.dismiss()
    }

    private fun setViews(
        binding: DialogSingleChoiceAlertBinding,
        dialog: AlertDialog,
    ) {
        binding.radioListDialogTitle.text = titleText

        if (messageText.isEmpty()) {
            binding.radioListDialogMessage.gone()
        } else {
            binding.radioListDialogMessage.text = messageText
        }

        optionList.forEachIndexed { index, option ->
            val radioButton = RadioButton(context, null)
            radioButton.id = index + 1
            radioButton.text = option
            val params = RadioGroup.LayoutParams(RadioGroup.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            radioButton.layoutParams = params
            binding.radioListDialogRadioGroup.addView(radioButton)
        }

        with(binding.radioListDialogRadioGroup) {
            selectedOption?.let { check(it) }
            setOnCheckedChangeListener { _, checkedId ->
                listener.onRadioItemSelected(checkedId)
                binding.radioListDialogPositiveButton.isEnabled = true
            }
        }

        binding.radioListDialogPositiveButton.text = positiveButtonText
        binding.radioListDialogPositiveButton.setOnClickListener {
            listener.onPositiveButtonClicked(binding.radioListDialogRadioGroup.checkedRadioButtonId)
            dialog.dismiss()
        }
        binding.radioListDialogPositiveButton.isEnabled = selectedOption != null

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
