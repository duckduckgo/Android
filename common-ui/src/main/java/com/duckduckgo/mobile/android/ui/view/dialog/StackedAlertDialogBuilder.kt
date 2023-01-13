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
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.mobile.android.databinding.DialogStackedAlertBinding
import com.duckduckgo.mobile.android.ui.view.button.DaxButtonGhost
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StackedAlertDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onButtonClicked(position: Int) {}
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
    var stackedButtonList: MutableList<CharSequence> = mutableListOf()
        private set

    fun setHeaderImageResource(@DrawableRes drawableId: Int): StackedAlertDialogBuilder {
        headerImageDrawableId = drawableId
        return this
    }

    fun setTitle(@StringRes textId: Int): StackedAlertDialogBuilder {
        titleText = context.getText(textId)
        return this
    }

    fun setTitle(text: CharSequence): StackedAlertDialogBuilder {
        titleText = text
        return this
    }

    fun setMessage(@StringRes textId: Int): StackedAlertDialogBuilder {
        messageText = context.getText(textId)
        return this
    }

    fun setMessage(text: CharSequence): StackedAlertDialogBuilder {
        messageText = text
        return this
    }

    fun setStackedButtons(@StringRes stackedButtonTextId: List<Int>): StackedAlertDialogBuilder {
        stackedButtonTextId.forEach {
            stackedButtonList.add(context.getText(it))
        }
        return this
    }

    fun addEventListener(eventListener: EventListener): StackedAlertDialogBuilder {
        listener = eventListener
        return this
    }

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogStackedAlertBinding = DialogStackedAlertBinding.inflate(LayoutInflater.from(context))

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

    override fun isShowing(): Boolean = dialog?.isShowing == true
    private fun setViews(
        binding: DialogStackedAlertBinding,
        dialog: AlertDialog,
    ) {
        if (headerImageDrawableId > 0) {
            binding.stackedAlertDialogImage.setImageResource(headerImageDrawableId)
        } else {
            binding.stackedAlertDialogImage.gone()
        }

        binding.stackedAlertDialogTitle.text = titleText

        if (messageText.isEmpty()) {
            binding.stackedlertDialogMessage.gone()
        } else {
            binding.stackedlertDialogMessage.text = messageText
        }

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        stackedButtonList.forEachIndexed { index, text ->
            val button = DaxButtonGhost(context, null)
            button.text = text
            button.layoutParams = buttonParams

            button.setOnClickListener {
                listener.onButtonClicked(index)
                dialog.dismiss()
            }

            binding.stackedAlertDialogButtonLayout.addView(button)
        }
    }

    private fun checkRequiredFieldsSet() {
        if (stackedButtonList.isEmpty()) {
            throw Exception("VerticallyStackedAlertDialog: You must always provide a list of buttons")
        }
        if (titleText.isEmpty()) {
            throw Exception("TextAlertDialog: You must always provide a Title")
        }
    }
}
