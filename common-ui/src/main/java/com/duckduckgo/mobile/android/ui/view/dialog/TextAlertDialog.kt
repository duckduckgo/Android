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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.databinding.DialogTextAlertBinding
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TextAlertDialog(val builder: Builder) : DialogFragment() {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onPositiveButtonClicked() {}
        open fun onNegativeButtonClicked() {}
    }

    internal class DefaultEventListener : EventListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        builder.listener.onDialogShown()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding: DialogTextAlertBinding = DialogTextAlertBinding.inflate(layoutInflater)

        if (builder.headerImageDrawableId > 0) {
            binding.textAlertDialogImage.setImageResource(builder.headerImageDrawableId)
        } else {
            binding.textAlertDialogImage.gone()
        }

        binding.textAlertDialogTitle.text = builder.titleText

        if (builder.messageText.isEmpty()) {
            binding.textAlertDialogMessage.gone()
        } else {
            binding.textAlertDialogMessage.text = builder.messageText
        }

        binding.textAlertDialogPositiveButton.text = builder.positiveButtonText
        binding.textAlertDialogPositiveButton.setOnClickListener {
            builder.listener.onPositiveButtonClicked()
            dismiss()
        }

        binding.textAlertDialogCancelButton.text = builder.negativeButtonText
        binding.textAlertDialogCancelButton.setOnClickListener {
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
        const val TAG_TEXT_ALERT_DIALOG = "TextAlertDialog"
    }

    class Builder(val context: Context) {

        var listener: EventListener = DefaultEventListener()

        var titleText: CharSequence = ""
        var messageText: CharSequence = ""

        var headerImageDrawableId = 0

        var positiveButtonText: CharSequence = ""
        var negativeButtonText: CharSequence = ""

        fun setHeaderImageResource(
            @DrawableRes drawableId: Int
        ): Builder {
            headerImageDrawableId = drawableId
            return this
        }

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

        fun build(): TextAlertDialog {
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
            return TextAlertDialog(this)
        }
    }
}
