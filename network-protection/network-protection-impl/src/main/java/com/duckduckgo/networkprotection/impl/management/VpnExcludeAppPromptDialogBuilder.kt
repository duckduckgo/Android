/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.management

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.button.DaxButtonGhost
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType
import com.duckduckgo.common.ui.view.text.DaxTextView.TextType.Secondary
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography
import com.duckduckgo.common.ui.view.text.DaxTextView.Typography.Body1
import com.duckduckgo.mobile.android.R
import com.duckduckgo.networkprotection.impl.databinding.DialogVpnDisableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class VpnExcludeAppPromptDialogBuilder(val context: Context) : DaxAlertDialog {

    abstract class EventListener {
        open fun onDialogShown() {}
        open fun onDialogDismissed() {}
        open fun onDialogCancelled() {}
        open fun onButtonClicked(
            position: Int,
            dontShow: Boolean,
        ) {
        }
    }

    internal class DefaultEventListener : EventListener()

    private var dialog: AlertDialog? = null

    private var listener: EventListener = DefaultEventListener()
    private var titleText: CharSequence = ""
    private var messageText: CharSequence = ""
    private var stackedButtonList: MutableList<CharSequence> = mutableListOf()
    private var isDestructiveVersion: Boolean = false

    fun setTitle(@StringRes textId: Int): VpnExcludeAppPromptDialogBuilder {
        titleText = context.getText(textId)
        return this
    }

    fun setTitle(text: CharSequence): VpnExcludeAppPromptDialogBuilder {
        titleText = text
        return this
    }

    fun setMessage(@StringRes textId: Int): VpnExcludeAppPromptDialogBuilder {
        messageText = context.getText(textId)
        return this
    }

    fun setMessage(text: CharSequence): VpnExcludeAppPromptDialogBuilder {
        messageText = text
        return this
    }

    fun setStackedButtons(@StringRes stackedButtonTextId: List<Int>): VpnExcludeAppPromptDialogBuilder {
        stackedButtonTextId.forEach {
            stackedButtonList.add(context.getText(it))
        }
        return this
    }

    fun setDestructiveButtons(isDestructive: Boolean): VpnExcludeAppPromptDialogBuilder {
        isDestructiveVersion = isDestructive
        return this
    }

    fun addEventListener(eventListener: EventListener): VpnExcludeAppPromptDialogBuilder {
        listener = eventListener
        return this
    }

    override fun build(): DaxAlertDialog {
        checkRequiredFieldsSet()
        val binding: DialogVpnDisableBinding = DialogVpnDisableBinding.inflate(LayoutInflater.from(context))
        binding.vpnDontShowAgainMessage.also {
            it.setTextAppearance(Typography.getTextAppearanceStyle(Body1))
            it.setTextColor(
                ContextCompat.getColorStateList(
                    context,
                    TextType.getTextColorStateList(Secondary),
                ),
            )
        }

        val dialogBuilder = MaterialAlertDialogBuilder(context, R.style.Widget_DuckDuckGo_Dialog)
            .setView(binding.root)
            .apply {
                setCancelable(false)
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
        binding: DialogVpnDisableBinding,
        dialog: AlertDialog,
    ) {
        binding.vpnExcludeAppPromptTitle.text = titleText
        binding.vpnExcludeAppPromptMessage.text = messageText

        addButtons(binding, stackedButtonList, dialog)
    }

    private fun addButtons(
        binding: DialogVpnDisableBinding,
        stackedButtonList: MutableList<CharSequence>,
        dialog: AlertDialog,
    ) {
        val root = binding.vpnExcludeAppPromptDialogButtonLayout
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        stackedButtonList.forEachIndexed { index, text ->
            val button = when {
                isDestructiveVersion && index == stackedButtonList.lastIndex -> {
                    val ghostButton = DaxButtonGhost(context, null)
                    ghostButton.setTextColor(
                        ContextCompat.getColorStateList(
                            context,
                            R.color.destructive_text_color_selector,
                        ),
                    )
                    ghostButton
                }

                isDestructiveVersion -> {
                    val ghostButton = DaxButtonGhost(context, null)
                    ghostButton.setTextColor(
                        ContextCompat.getColorStateList(
                            context,
                            R.color.secondary_text_color_selector,
                        ),
                    )
                    ghostButton
                }

                else -> {
                    DaxButtonGhost(context, null)
                }
            }

            button.text = text
            button.layoutParams = buttonParams

            button.setOnClickListener {
                listener.onButtonClicked(index, binding.vpnDontShowAgainMessage.isChecked)
                dialog.dismiss()
            }

            root.addView(button)
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
