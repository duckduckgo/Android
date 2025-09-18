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
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.duckduckgo.common.ui.view.button.ButtonType
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.getColorFromAttr
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
    private var messageClickable: Boolean = false
    private var headerImageDrawableId = 0
    private var positiveButtonText: CharSequence = ""
    private var positiveButtonType: ButtonType = ButtonType.PRIMARY
    private var negativeButtonText: CharSequence = ""
    private var negativeButtonType: ButtonType? = null

    private var isCancellable: Boolean = false
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

    fun setClickableMessage(textSequence: CharSequence, annotation: String, onClick: () -> Unit): TextAlertDialogBuilder {
        val fullText = textSequence as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                onClick()
            }
        }

        annotations?.find { it.value == annotation }?.let {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    UnderlineSpan(),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                setSpan(
                    ForegroundColorSpan(
                        context.getColorFromAttr(R.attr.daxColorAccentBlue),
                    ),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }

        messageText = spannableString
        messageClickable = true

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

    fun setPositiveButton(
        @StringRes textId: Int,
        buttonType: ButtonType = ButtonType.PRIMARY,
    ): TextAlertDialogBuilder {
        positiveButtonText = context.getText(textId)
        positiveButtonType = buttonType
        return this
    }

    fun setPositiveButton(
        title: String,
        buttonType: ButtonType = ButtonType.PRIMARY,
    ): TextAlertDialogBuilder {
        positiveButtonText = title
        positiveButtonType = buttonType
        return this
    }

    fun setNegativeButton(
        @StringRes textId: Int,
        buttonType: ButtonType = ButtonType.GHOST,
    ): TextAlertDialogBuilder {
        negativeButtonText = context.getText(textId)
        negativeButtonType = buttonType
        return this
    }

    fun setCancellable(cancellable: Boolean): TextAlertDialogBuilder {
        isCancellable = cancellable
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
            if (messageClickable) {
                binding.textAlertDialogMessage.movementMethod = LinkMovementMethod.getInstance()
            }
        }

        setButtons(binding, dialog)
    }

    private fun setButtons(
        binding: DialogTextAlertBinding,
        dialog: AlertDialog,
    ) {
        // add buttons
        val negativeButton = negativeButtonType
        if (negativeButton != null) {
            val negativeButtonView = negativeButton.getView(context)
            setButtonListener(negativeButtonView, negativeButtonText, dialog) { listener.onNegativeButtonClicked() }

            binding.textAlertDialogButtonContainer.addView(negativeButtonView)
            val layoutParams = negativeButtonView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.setMargins(
                layoutParams.leftMargin,
                layoutParams.topMargin,
                context.resources.getDimensionPixelSize(R.dimen.keyline_2),
                layoutParams.bottomMargin,
            )
            negativeButtonView.layoutParams = layoutParams
        }

        val positiveButtonView = positiveButtonType.getView(context)
        setButtonListener(positiveButtonView, positiveButtonText, dialog) { listener.onPositiveButtonClicked() }
        binding.textAlertDialogButtonContainer.addView(positiveButtonView)

        binding.textAlertDialogCheckBox.setOnCheckedChangeListener { compoundButton, checked ->
            listener.onCheckedChanged(checked)
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
