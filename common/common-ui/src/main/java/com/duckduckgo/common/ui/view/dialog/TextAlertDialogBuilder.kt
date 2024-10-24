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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TextAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onDialogCancelled() {}
        open fun onPositiveButtonClicked() {}
        open fun onNegativeButtonClicked() {}
        open fun onCheckedChanged(checked: Boolean) {}
    }

    internal class DefaultEventListener : EventListener()

    private var dialog: AlertDialog? = null

    private var listener: EventListener = DefaultEventListener()
    private var titleText: CharSequence = ""
    private var messageText: CharSequence = ""
    private var headerImageDrawableId = 0
    private var positiveButtonText: CharSequence = ""
    private var negativeButtonText: CharSequence = ""
    private var isCancellable: Boolean = false
    private var isDestructiveVersion: Boolean = false
    private var isCheckboxEnabled: Boolean = false
    private var checkBoxText: CharSequence = ""

    fun setHeaderImageResource(@DrawableRes drawableId: Int): TextAlertDialogBuilder {
        headerImageDrawableId = drawableId
        return this
    }

    fun setTitle(@StringRes textId: Int): TextAlertDialogBuilder {
        titleText = context.getText(textId)
        return this
    }

    fun setMessage(@StringRes textId: Int): TextAlertDialogBuilder {
        messageText = context.getText(textId)
        return this
    }

    fun setTitle(text: CharSequence): TextAlertDialogBuilder {
        titleText = text
        return this
    }

    fun setMessage(text: CharSequence): TextAlertDialogBuilder {
        messageText = text
        return this
    }

    fun setPositiveButton(@StringRes textId: Int): TextAlertDialogBuilder {
        positiveButtonText = context.getText(textId)
        return this
    }

    fun setNegativeButton(@StringRes textId: Int): TextAlertDialogBuilder {
        negativeButtonText = context.getText(textId)
        return this
    }

    fun setCancellable(cancellable: Boolean): TextAlertDialogBuilder {
        isCancellable = cancellable
        return this
    }

    fun setDestructiveButtons(isDestructive: Boolean): TextAlertDialogBuilder {
        isDestructiveVersion = isDestructive
        return this
    }

    fun addEventListener(eventListener: EventListener): TextAlertDialogBuilder {
        listener = eventListener
        return this
    }

    fun setCheckBoxText(@StringRes textId: Int): TextAlertDialogBuilder {
        isCheckboxEnabled = true
        checkBoxText = context.getText(textId)
        return this
    }

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogTextAlertBinding = DialogTextAlertBinding.inflate(LayoutInflater.from(context))

        if (isCheckboxEnabled) {
            binding.textAlertDialogCheckBox.text = checkBoxText
            binding.textAlertDialogCheckBox.show()
        }

        val ghostButtonDestructive = DaxButtonGhost(context, null)
        ghostButtonDestructive.setTextColor(
            ContextCompat.getColorStateList(
                context,
                R.color.destructive_text_color_selector,
            ),
        )
        ghostButtonDestructive.setText(checkBoxText)

        val ghostButton = DaxButtonGhost(context, null)
        ghostButton.setTextColor(
            ContextCompat.getColorStateList(
                context,
                R.color.secondary_text_color_selector,
            ),
        )
        ghostButton.setText(checkBoxText)

        binding.textAlertDialogButtonContainer.addView(ghostButton)
        binding.textAlertDialogButtonContainer.addView(ghostButtonDestructive)

        val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.Widget_DuckDuckGo_Dialog)
            .setView(binding.root)
            .setCancelable(isCancellable)
            .apply {
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

    override fun isShowing(): Boolean = dialog?.isShowing == true

    private fun setViews(
        binding: DialogTextAlertBinding,
        dialog: AlertDialog,
    ) {
        if (headerImageDrawableId > 0) {
            binding.textAlertDialogImage.setImageResource(headerImageDrawableId)
        } else {
            binding.textAlertDialogImage.gone()
        }

        binding.textAlertDialogTitle.text = titleText

        if (messageText.isEmpty()) {
            binding.textAlertDialogMessage.gone()
        } else {
            binding.textAlertDialogMessage.text = messageText
        }

        setButtons(binding, dialog)
    }

    private fun setButtons(
        binding: DialogTextAlertBinding,
        dialog: AlertDialog,
    ) {
        binding.textAlertDialogPositiveButton.isVisible = !isDestructiveVersion
        binding.textAlertDialogPositiveDestructiveButton.isVisible = isDestructiveVersion
        binding.textAlertDialogCancelButton.isVisible = !isDestructiveVersion
        binding.textAlertDialogCancelDestructiveButton.isVisible = isDestructiveVersion

        if (negativeButtonText.isEmpty()) {
            binding.textAlertDialogCancelDestructiveButton.gone()
            binding.textAlertDialogCancelButton.gone()
        }

        binding.textAlertDialogCheckBox.setOnCheckedChangeListener { compoundButton, checked ->
            listener.onCheckedChanged(checked)
        }

        if (isDestructiveVersion) {
            setButtonListener(binding.textAlertDialogPositiveDestructiveButton, positiveButtonText, dialog) {
                listener.onPositiveButtonClicked()
            }
            setButtonListener(binding.textAlertDialogCancelDestructiveButton, negativeButtonText, dialog) { listener.onNegativeButtonClicked() }
            // no need to get fancy, just change the button textColor
            binding.textAlertDialogCancelDestructiveButton.setTextColor(
                ContextCompat.getColorStateList(
                    context,
                    R.color.secondary_text_color_selector,
                ),
            )
        } else {
            setButtonListener(binding.textAlertDialogPositiveButton, positiveButtonText, dialog) { listener.onPositiveButtonClicked() }
            setButtonListener(binding.textAlertDialogCancelButton, negativeButtonText, dialog) { listener.onNegativeButtonClicked() }
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
            throw Exception("TextAlertDialog: You must always provide a Positive Button")
        }
        if (titleText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Title")
        }
    }
}
