/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.view

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import kotlinx.android.synthetic.main.content_dax_dialog.*

interface DaxDialog {
    fun setDaxText(daxText: String)
    fun setButtonText(buttonText: String)
    fun setDialogAndStartAnimation()
    fun getDaxDialog(): DialogFragment
    fun setDaxDialogListener(listener: DaxDialogListener)
}

interface DaxDialogListener {
    fun onDaxDialogDismiss()
    fun onDaxDialogPrimaryCtaClick()
    fun onDaxDialogSecondaryCtaClick()
    fun onDaxDialogHideClick()
}

class TypewriterDaxDialog : DialogFragment(), DaxDialog {

    private var daxText: String = ""
    private var primaryButtonText: String = ""
    private var secondaryButtonText: String = ""
    private var toolbarDimmed: Boolean = true
    private var dismissible: Boolean = true
    private var typingDelayInMs: Long = DEFAULT_TYPING_DELAY
    private var showHideButton: Boolean = true

    private var daxDialogListener: DaxDialogListener? = null

    override fun setDaxDialogListener(listener: DaxDialogListener) {
        daxDialogListener = listener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.content_dax_dialog, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val window = dialog.window
        val attributes = window?.attributes

        attributes?.gravity = Gravity.BOTTOM
        window?.attributes = attributes
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.run {
            if (containsKey(ARG_DAX_TEXT)) {
                getString(ARG_DAX_TEXT)?.let { daxText = it }
            }
            if (containsKey(ARG_PRIMARY_CTA_TEXT)) {
                getString(ARG_PRIMARY_CTA_TEXT)?.let { primaryButtonText = it }
            }
            if (containsKey(ARG_SECONDARY_CTA_TEXT)) {
                getString(ARG_SECONDARY_CTA_TEXT)?.let { secondaryButtonText = it }
            }
            if (containsKey(ARG_DISMISSIBLE)) {
                dismissible = getBoolean(ARG_DISMISSIBLE)
            }
            if (containsKey(ARG_TOOLBAR_DIMMED)) {
                toolbarDimmed = getBoolean(ARG_TOOLBAR_DIMMED)
            }
            if (containsKey(ARG_TYPING_DELAY)) {
                typingDelayInMs = getLong(ARG_TYPING_DELAY)
            }
            if (containsKey(ARG_SHOW_HIDE_BUTTON)) {
                showHideButton = getBoolean(ARG_SHOW_HIDE_BUTTON)
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.DaxDialogFragment
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.attributes?.dimAmount = 0f
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setDialogAndStartAnimation()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity != null) {
            dialogText?.cancelAnimation()
            daxDialogListener?.onDaxDialogDismiss()
        }
        daxDialogListener = null
        super.onDismiss(dialog)
    }

    override fun getDaxDialog(): DialogFragment = this

    override fun setDaxText(daxText: String) {
        this.daxText = daxText
    }

    override fun setButtonText(buttonText: String) {
        this.primaryButtonText = buttonText
    }

    override fun setDialogAndStartAnimation() {
        setDialog()
        setListeners()
        dialogText.startTypingAnimation(daxText, true)
    }

    private fun setListeners() {
        hideText.setOnClickListener {
            dialogText.cancelAnimation()
            daxDialogListener?.onDaxDialogHideClick()
            dismiss()
        }

        primaryCta.setOnClickListener {
            dialogText.cancelAnimation()
            daxDialogListener?.onDaxDialogPrimaryCtaClick()
            dismiss()
        }

        secondaryCta.setOnClickListener {
            dialogText.cancelAnimation()
            daxDialogListener?.onDaxDialogSecondaryCtaClick()
            dismiss()
        }

        dialogContainer.setOnClickListener {
            if (dismissible) {
                dialogText.cancelAnimation()
                dismiss()
            } else {
                dialogText.finishAnimation(daxText)
                dismissible = true
            }
        }
    }

    private fun setDialog() {
        if (context == null) {
            dismiss()
            return
        }

        context?.let {
            val toolbarColor = if (toolbarDimmed) getColor(it, R.color.dimmed) else getColor(it, android.R.color.transparent)
            toolbarDialogLayout.setBackgroundColor(toolbarColor)
            hiddenText.text = daxText.html(it)
            primaryCta.text = primaryButtonText
            secondaryCta.text = secondaryButtonText
            secondaryCta.visibility = if (secondaryButtonText.isEmpty()) View.GONE else View.VISIBLE
            dialogText.typingDelayInMs = typingDelayInMs
            hideText.visibility = if (showHideButton) View.VISIBLE else View.GONE
        }
    }

    companion object {

        fun newInstance(
            daxText: String,
            primaryButtonText: String,
            secondaryButtonText: String? = "",
            toolbarDimmed: Boolean = true,
            dismissible: Boolean = true,
            typingDelayInMs: Long = DEFAULT_TYPING_DELAY,
            showHideButton: Boolean = true
        ): TypewriterDaxDialog {
            return TypewriterDaxDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DAX_TEXT, daxText)
                    putString(ARG_PRIMARY_CTA_TEXT, primaryButtonText)
                    putString(ARG_SECONDARY_CTA_TEXT, secondaryButtonText)
                    putBoolean(ARG_TOOLBAR_DIMMED, toolbarDimmed)
                    putBoolean(ARG_DISMISSIBLE, dismissible)
                    putLong(ARG_TYPING_DELAY, typingDelayInMs)
                    putBoolean(ARG_SHOW_HIDE_BUTTON, showHideButton)
                }
            }
        }

        private const val DEFAULT_TYPING_DELAY: Long = 20
        private const val ARG_DAX_TEXT = "daxText"
        private const val ARG_PRIMARY_CTA_TEXT = "primaryCtaText"
        private const val ARG_SECONDARY_CTA_TEXT = "secondaryCtaText"
        private const val ARG_TOOLBAR_DIMMED = "toolbarDimmed"
        private const val ARG_DISMISSIBLE = "isDismissible"
        private const val ARG_TYPING_DELAY = "typingDelay"
        private const val ARG_SHOW_HIDE_BUTTON = "showHideButton"
    }
}
