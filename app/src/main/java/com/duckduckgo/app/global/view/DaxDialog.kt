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
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import kotlinx.android.synthetic.main.content_dax_dialog.*

interface DaxDialog {
    fun setDaxText(daxText: String)
    fun setButtonText(buttonText: String)
    fun setDialogAndStartAnimation()
    fun onAnimationFinishedListener(onAnimationFinished: () -> Unit)
    fun setPrimaryCtaClickListener(clickListener: () -> Unit)
    fun setSecondaryCtaClickListener(clickListener: () -> Unit)
    fun setHideClickListener(clickListener: () -> Unit)
    fun setDismissListener(clickListener: () -> Unit)
    fun getDaxDialog(): DialogFragment
}

class TypewriterDaxDialog(
    private var daxText: String,
    private var primaryButtonText: String,
    private var secondaryButtonText: String? = "",
    private val toolbarDimmed: Boolean = true,
    private val dismissible: Boolean = true,
    private val typingDelayInMs: Long = 20
) : DialogFragment(), DaxDialog {

    private var onAnimationFinished: () -> Unit = {}
    private var primaryCtaClickListener: () -> Unit = { dismiss() }
    private var secondaryCtaClickListener: (() -> Unit)? = null
    private var hideClickListener: () -> Unit = { dismiss() }
    private var dismissListener: () -> Unit = { }

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
        dialogText?.cancelAnimation()
        dismissListener()
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
        dialogText.startTypingAnimation(daxText, true, onAnimationFinished)
    }

    override fun onAnimationFinishedListener(onAnimationFinished: () -> Unit) {
        this.onAnimationFinished = onAnimationFinished
    }

    override fun setPrimaryCtaClickListener(clickListener: () -> Unit) {
        primaryCtaClickListener = clickListener
    }

    override fun setSecondaryCtaClickListener(clickListener: () -> Unit) {
        secondaryCtaClickListener = clickListener
    }

    override fun setHideClickListener(clickListener: () -> Unit) {
        hideClickListener = clickListener
    }

    override fun setDismissListener(clickListener: () -> Unit) {
        dismissListener = clickListener
    }

    private fun setListeners() {
        hideText.setOnClickListener {
            dialogText.cancelAnimation()
            hideClickListener()
        }

        primaryCta.setOnClickListener {
            dialogText.cancelAnimation()
            primaryCtaClickListener()
        }

        secondaryCtaClickListener?.let {
            secondaryCta.setOnClickListener {
                dialogText.cancelAnimation()
                it()
            }
        }

        if (dismissible) {
            dialogContainer.setOnClickListener {
                dialogText.cancelAnimation()
                dismiss()
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
            secondaryCta.visibility = if (secondaryButtonText.isNullOrEmpty()) View.GONE else View.VISIBLE
            dialogText.typingDelayInMs = typingDelayInMs
        }
    }
}

class DaxDialogHighlightView(
    daxDialog: TypewriterDaxDialog
) : DaxDialog by daxDialog {

    fun startHighlightViewAnimation(targetView: View, duration: Long = 400, timesBigger: Float = 0f) {
        val highlightImageView = addHighlightView(targetView, timesBigger)
        val scaleAnimation = buildScaleAnimation(duration)
        highlightImageView?.startAnimation(scaleAnimation)
    }

    private fun buildScaleAnimation(duration: Long = 400): Animation {
        val scaleAnimation = ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        scaleAnimation.duration = duration
        scaleAnimation.fillAfter = true
        scaleAnimation.interpolator = OvershootInterpolator(OVERSHOOT_TENSION)
        return scaleAnimation
    }

    private fun addHighlightView(targetView: View, timesBigger: Float): View? {
        return getDaxDialog().activity?.let {
            val highlightImageView = ImageView(getDaxDialog().context)
            highlightImageView.id = View.generateViewId()
            highlightImageView.setImageResource(R.drawable.ic_circle)

            val rec = Rect()
            val window = it.window
            window.decorView.getWindowVisibleDisplayFrame(rec)

            val statusBarHeight = rec.top
            val width = targetView.width
            val height = targetView.height
            val locationOnScreen: IntArray = intArrayOf(0, 0)
            targetView.getLocationOnScreen(locationOnScreen)

            val timesBiggerX: Float = width * timesBigger
            val timesBiggerY: Float = height * timesBigger

            val params = RelativeLayout.LayoutParams((width + timesBiggerX).toInt(), (height + timesBiggerY).toInt())
            params.leftMargin = locationOnScreen[0] - (timesBiggerX / 2).toInt()
            params.topMargin = (locationOnScreen[1] - statusBarHeight) - (timesBiggerY / 2).toInt()
            getDaxDialog().dialogContainer.addView(highlightImageView, params)
            highlightImageView
        }
    }

    companion object {
        const val OVERSHOOT_TENSION = 3f
    }
}