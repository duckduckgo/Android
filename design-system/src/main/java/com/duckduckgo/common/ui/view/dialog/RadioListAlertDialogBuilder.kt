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

package com.duckduckgo.common.ui.view.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RadioGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.common.ui.view.button.ButtonType
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.RadioButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogSingleChoiceAlertBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RadioListAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onDialogCancelled() {}
        open fun onRadioItemSelected(selectedItem: Int) {}
        open fun onPositiveButtonClicked(selectedItem: Int) {}
        open fun onNegativeButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    private var dialog: AlertDialog? = null

    private var listener: EventListener = DefaultEventListener()
    private var titleText: CharSequence = ""
    private var messageText: CharSequence = ""
    private var positiveButtonText: CharSequence = ""
    private var positiveButtonType: ButtonType = ButtonType.PRIMARY
    private var negativeButtonText: CharSequence = ""
    private var negativeButtonType: ButtonType? = null
    private var optionList: MutableList<CharSequence> = mutableListOf()
    private var selectedOption: Int? = null
    private var isCancelable: Boolean = false

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

    @Deprecated(message = "options should be passed as List<Int> so we make sure they are localised")
    @JvmName("setOptionsString")
    fun setOptions(
        stackedButtonTextId: List<String>,
        selectedItem: Int? = null,
    ): RadioListAlertDialogBuilder {
        stackedButtonTextId.forEach {
            optionList.add(it)
        }
        selectedOption = selectedItem
        return this
    }

    fun setPositiveButton(
        @StringRes textId: Int,
        buttonType: ButtonType = ButtonType.PRIMARY,
    ): RadioListAlertDialogBuilder {
        positiveButtonText = context.getText(textId)
        positiveButtonType = buttonType
        return this
    }

    fun setNegativeButton(
        @StringRes textId: Int,
        buttonType: ButtonType = ButtonType.GHOST,
    ): RadioListAlertDialogBuilder {
        negativeButtonText = context.getText(textId)
        negativeButtonType = buttonType
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
                setCancelable(isCancelable)
                setOnDismissListener { listener.onDialogDismissed() }
                setOnCancelListener { listener.onDialogCancelled() }
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

    fun setCancelable(cancelable: Boolean): RadioListAlertDialogBuilder {
        isCancelable = cancelable
        return this
    }

    override fun isShowing(): Boolean = dialog?.isShowing == true
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
                // enable positive button added to the container
                binding.radioListAlertDialogButtonContainer.getChildAt(1).isEnabled = true
            }
        }

        setButtons(binding, dialog)
    }

    private fun setButtons(
        binding: DialogSingleChoiceAlertBinding,
        dialog: AlertDialog,
    ) {
        val positiveButtonView = positiveButtonType.getView(context)
        val negativeButton = negativeButtonType
        if (negativeButton != null) {
            val negativeButtonView = negativeButton.getView(context)
            setButtonListener(negativeButtonView, negativeButtonText, dialog) { listener.onNegativeButtonClicked() }

            binding.radioListAlertDialogButtonContainer.addView(negativeButtonView)
            val layoutParams = negativeButtonView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(
                context.resources.getDimensionPixelSize(R.dimen.keyline_2),
                layoutParams.topMargin,
                layoutParams.rightMargin,
                layoutParams.bottomMargin,
            )
            positiveButtonView.layoutParams = layoutParams
        }

        positiveButtonView.isEnabled = selectedOption != null
        binding.radioListAlertDialogButtonContainer.addView(positiveButtonView)
        setButtonListener(positiveButtonView, positiveButtonText, dialog) {
            listener.onPositiveButtonClicked(binding.radioListDialogRadioGroup.checkedRadioButtonId)
        }
    }

    private fun setButtonListener(
        button: DaxButton,
        text: CharSequence,
        dialog: AlertDialog,
        onClick: () -> Unit,

    ) {
        button.text = text
        button.setOnClickListener {
            onClick.invoke()
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
