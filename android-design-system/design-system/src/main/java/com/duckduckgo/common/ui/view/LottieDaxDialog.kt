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

package com.duckduckgo.common.ui.view

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.DialogFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.databinding.ViewDaxDialogAnimatedBinding

class LottieDaxDialog : DialogFragment(R.layout.view_dax_dialog_animated), DaxDialog {

    private val binding: ViewDaxDialogAnimatedBinding by viewBinding()

    private var title: String = ""
    private var description: String = ""
    private var lottieRes: Int = -1
    private var primaryButtonText: String = ""
    private var secondaryButtonText: String = ""
    private var hideButtonText: String = ""
    private var dismissible: Boolean = false
    private var showHideButton: Boolean = true

    private var daxDialogListener: DaxDialogListener? = null

    override fun setDaxDialogListener(listener: DaxDialogListener?) {
        daxDialogListener = listener
    }

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
            if (containsKey(ARG_TITLE_TEXT)) {
                getString(ARG_TITLE_TEXT)?.let { title = it }
            }
            if (containsKey(ARG_DESCRIPTION_TEXT)) {
                getString(ARG_DESCRIPTION_TEXT)?.let { description = it }
            }
            if (containsKey(ARG_PRIMARY_CTA_TEXT)) {
                getString(ARG_PRIMARY_CTA_TEXT)?.let { primaryButtonText = it }
            }
            if (containsKey(ARG_HIDE_CTA_TEXT)) {
                getString(ARG_HIDE_CTA_TEXT)?.let { hideButtonText = it }
            }
            if (containsKey(ARG_SECONDARY_CTA_TEXT)) {
                getString(ARG_SECONDARY_CTA_TEXT)?.let { secondaryButtonText = it }
            }
            if (containsKey(ARG_DISMISSIBLE)) {
                dismissible = getBoolean(ARG_DISMISSIBLE)
            }
            if (containsKey(ARG_LOTTIE_RES)) {
                lottieRes = getInt(ARG_LOTTIE_RES)
            }
            if (containsKey(ARG_SHOW_HIDE_BUTTON)) {
                showHideButton = getBoolean(ARG_SHOW_HIDE_BUTTON)
            }
        }
    }

    override fun getTheme(): Int {
        return R.style.Widget_DuckDuckGo_DaxDialogFragment
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.attributes?.dimAmount = 0f
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setDialogAndStartAnimation()
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity != null) {
            binding.animation.cancelAnimation()
            daxDialogListener?.onDaxDialogDismiss()
        }
        daxDialogListener = null
        super.onDismiss(dialog)
    }

    private fun setDialogAndStartAnimation() {
        setDialog()
        setListeners()
        binding.animation.apply {
            setAnimation(lottieRes)
            playAnimation()
        }
    }

    private fun setListeners() {
        with(binding) {
            hideText.setOnClickListener {
                binding.animation.cancelAnimation()
                daxDialogListener?.onDaxDialogHideClick()
                dismiss()
            }

            primaryCta.setOnClickListener {
                binding.animation.cancelAnimation()
                daxDialogListener?.onDaxDialogPrimaryCtaClick()
                dismiss()
            }

            secondaryCta.setOnClickListener {
                binding.animation.cancelAnimation()
                daxDialogListener?.onDaxDialogSecondaryCtaClick()
                dismiss()
            }

            dialogContainer.setOnClickListener {
                if (dismissible) {
                    binding.animation.cancelAnimation()
                    dismiss()
                } else {
                    if (binding.animation.isAnimating) {
                        binding.animation.cancelAnimation()
                    }
                }
            }
        }
    }

    private fun setDialog() {
        if (context == null) {
            dismiss()
            return
        }

        context?.let {
            with(binding) {
                titleText.text = HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY)
                descriptionText.text = description
                hideText.text = hideButtonText
                primaryCta.text = primaryButtonText
                secondaryCta.text = secondaryButtonText
                secondaryCta.visibility = if (secondaryButtonText.isEmpty()) View.GONE else View.VISIBLE
                hideText.visibility = if (showHideButton) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {

        fun newInstance(
            titleText: String,
            descriptionText: String,
            lottieRes: Int,
            primaryButtonText: String,
            secondaryButtonText: String? = "",
            hideButtonText: String,
            dismissible: Boolean = false,
            showHideButton: Boolean = true,
        ): LottieDaxDialog {
            return LottieDaxDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE_TEXT, titleText)
                    putString(ARG_DESCRIPTION_TEXT, descriptionText)
                    putInt(ARG_LOTTIE_RES, lottieRes)
                    putString(ARG_PRIMARY_CTA_TEXT, primaryButtonText)
                    putString(ARG_HIDE_CTA_TEXT, hideButtonText)
                    putString(ARG_SECONDARY_CTA_TEXT, secondaryButtonText)
                    putBoolean(ARG_DISMISSIBLE, dismissible)
                    putBoolean(ARG_SHOW_HIDE_BUTTON, showHideButton)
                }
            }
        }

        private const val ARG_TITLE_TEXT = "titleText"
        private const val ARG_DESCRIPTION_TEXT = "descriptionText"
        private const val ARG_LOTTIE_RES = "lottieRes"
        private const val ARG_PRIMARY_CTA_TEXT = "primaryCtaText"
        private const val ARG_HIDE_CTA_TEXT = "hideCtaText"
        private const val ARG_SECONDARY_CTA_TEXT = "secondaryCtaText"
        private const val ARG_DISMISSIBLE = "isDismissible"
        private const val ARG_SHOW_HIDE_BUTTON = "showHideButton"
    }
}
