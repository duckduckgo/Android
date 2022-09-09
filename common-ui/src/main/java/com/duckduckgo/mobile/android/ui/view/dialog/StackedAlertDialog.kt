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
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.databinding.DialogStackedAlertBinding
import com.duckduckgo.mobile.android.ui.view.button.ButtonGhostLarge
import com.duckduckgo.mobile.android.ui.view.gone
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class StackedAlertDialog(val builder: Builder) : DialogFragment() {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onButtonClicked(position: Int) {}
    }
    internal class DefaultEventListener: EventListener()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        builder.listener.onDialogShown()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val binding: DialogStackedAlertBinding = DialogStackedAlertBinding.inflate(layoutInflater)

        if (builder.headerImageDrawableId > 0) {
            binding.stackedAlertDialogImage.setImageResource(builder.headerImageDrawableId)
        } else {
            binding.stackedAlertDialogImage.gone()
        }

        binding.stackedAlertDialogTitle.text = builder.titleText

        if (builder.messageText.isEmpty()) {
            binding.stackedlertDialogMessage.gone()
        } else {
            binding.stackedlertDialogMessage.text = builder.messageText
        }

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        builder.stackedButtonList.forEachIndexed { index, text ->
            val button = ButtonGhostLarge(builder.context, null)
            button.text = text
            button.layoutParams = buttonParams

            button.setOnClickListener {
                builder.listener.onButtonClicked(index)
                dismiss()
            }

            binding.stackedAlertDialogButtonLayout.addView(button)
        }

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_Dialog
        )
            .setView(binding.root)

        return alertDialog.create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        builder.listener.onDialogDismissed()
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG_STACKED_ALERT_DIALOG = "VerticallyStackedAlertDialog"
    }

    class Builder(val context: Context) {

        var listener: EventListener = DefaultEventListener()

        var titleText: CharSequence = ""
        var messageText: CharSequence = ""

        var headerImageDrawableId = 0

        var stackedButtonList: MutableList<CharSequence> = mutableListOf()

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

        fun setStackedButtons(@StringRes stackedButtonTextId: List<Int>): Builder {
            stackedButtonTextId.forEach {
                stackedButtonList.add(context.getText(it))
            }
            return this
        }

        fun addEventListener(eventListener: EventListener): Builder {
            listener = eventListener
            return this
        }

        fun build(): StackedAlertDialog {
            val builder = this
            if (builder.stackedButtonList.isEmpty()) {
                throw Exception("VerticallyStackedAlertDialog: You must always provide a list of buttons")
            }
            if (builder.titleText.isEmpty()) {
                throw Exception("TextAlertDialog: You must always provide a Title")
            }
            return StackedAlertDialog(this)
        }
    }
}
