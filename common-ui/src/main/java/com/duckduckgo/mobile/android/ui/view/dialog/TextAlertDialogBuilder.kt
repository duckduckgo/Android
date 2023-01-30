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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TextAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onPositiveButtonClicked() {}
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
    var headerImageDrawableId = 0
        private set
    var positiveButtonText: CharSequence = ""
        private set
    var negativeButtonText: CharSequence = ""
        private set

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

    fun addEventListener(eventListener: EventListener): TextAlertDialogBuilder {
        listener = eventListener
        return this
    }

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogTextAlertBinding = DialogTextAlertBinding.inflate(LayoutInflater.from(context))

        val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.Widget_DuckDuckGo_Dialog)
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

        binding.textAlertDialogPositiveButton.text = positiveButtonText
        binding.textAlertDialogPositiveButton.setOnClickListener {
            listener.onPositiveButtonClicked()
            dialog.dismiss()
        }

        binding.textAlertDialogCancelButton.text = negativeButtonText
        binding.textAlertDialogCancelButton.setOnClickListener {
            listener.onNegativeButtonClicked()
            dialog.dismiss()
        }
    }

    private fun checkRequiredFieldsSet() {
        if (positiveButtonText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Positive Button")
        }
        if (negativeButtonText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Negative Button")
        }
        if (titleText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Title")
        }
    }
}
