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
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.viewbinding.ViewBinding
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.databinding.DialogCustomAlertBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CustomAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onPositiveButtonClicked() {}
        open fun onNegativeButtonClicked() {}
        open fun onDialogCancelled() {}
    }

    internal class DefaultEventListener : EventListener()

    private var dialog: AlertDialog? = null
    private var customBinding: ViewBinding? = null

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

    fun setTitle(@StringRes textId: Int): CustomAlertDialogBuilder {
        titleText = context.getText(textId)
        return this
    }

    fun setMessage(@StringRes textId: Int): CustomAlertDialogBuilder {
        messageText = context.getText(textId)
        return this
    }

    fun setTitle(text: CharSequence): CustomAlertDialogBuilder {
        titleText = text
        return this
    }

    fun setMessage(text: CharSequence): CustomAlertDialogBuilder {
        messageText = text
        return this
    }

    fun setPositiveButton(@StringRes textId: Int): CustomAlertDialogBuilder {
        positiveButtonText = context.getText(textId)
        return this
    }

    fun setNegativeButton(@StringRes textId: Int): CustomAlertDialogBuilder {
        negativeButtonText = context.getText(textId)
        return this
    }

    fun addEventListener(eventListener: EventListener): CustomAlertDialogBuilder {
        listener = eventListener
        return this
    }

    fun setView(binding: ViewBinding): CustomAlertDialogBuilder {
        customBinding = binding
        return this
    }

    override fun build(): DaxAlertDialog {
        val binding: DialogCustomAlertBinding = DialogCustomAlertBinding.inflate(LayoutInflater.from(context))
        binding.customDialogContent.addView(customBinding?.root)

        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
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

    override fun isShowing(): Boolean {
        return if (dialog != null) {
            dialog!!.isShowing
        } else {
            false
        }
    }

    private fun setViews(
        binding: DialogCustomAlertBinding,
        dialog: AlertDialog,
    ) {
        if (titleText.isEmpty()) {
            binding.customDialogTitle.gone()
        } else {
            binding.customDialogTitle.text = titleText
        }

        if (messageText.isEmpty()) {
            binding.customDialogMessage.gone()
        } else {
            binding.customDialogMessage.text = messageText
        }

        if (positiveButtonText.isEmpty()) {
            binding.customDialogPositiveButton.gone()
        } else {
            binding.customDialogPositiveButton.text = positiveButtonText
            binding.customDialogPositiveButton.setOnClickListener {
                listener.onPositiveButtonClicked()
                dialog.dismiss()
            }
        }

        if (negativeButtonText.isEmpty()) {
            binding.customDialogNegativeButton.gone()
        } else {
            binding.customDialogNegativeButton.text = negativeButtonText
            binding.customDialogNegativeButton.setOnClickListener {
                listener.onNegativeButtonClicked()
                dialog.dismiss()
            }
        }
    }
}
