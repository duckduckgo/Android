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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import android.view.Gravity
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.sheet_dax_dialog.*
import android.view.animation.Animation
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class DaxBottomSheetDialog(
    private val daxText: String,
    private val buttonText: String,
    private val dismissible: Boolean = true,
    private val typingDelayInMs: Long = 20
) :
    DialogFragment(), CoroutineScope {

    private val animationJob: Job = Job()
    private var typingAnimationJob: Job? = null
    private var afterAnimation: () -> Unit = {}
    private var clickListener: () -> Unit = { dismiss() }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + animationJob

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.sheet_dax_dialog, container, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val window = dialog.window
        val attributes = window?.attributes

        attributes?.gravity = Gravity.BOTTOM
        window?.attributes = attributes
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        typingAnimationJob?.cancel()
        super.onCancel(dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        primaryCta.text = buttonText

        primaryCta.setOnClickListener {
            typingAnimationJob?.cancel()
            clickListener()
        }

        hiddenText.text = daxText

        typingAnimationJob = launch {
            startTypingAnimation(daxText, afterAnimation)
        }

        if (dismissible) {
            dialogContainer.setOnClickListener {
                typingAnimationJob?.cancel()
                dismiss()
            }
        }

        dialogText.setOnClickListener {
            if ((typingAnimationJob as Job).isActive) {
                typingAnimationJob?.cancel()
                dialogText.text = daxText
                afterAnimation()
            }
        }
    }

    fun afterAnimationListener(afterAnimation: () -> Unit = {}) {
        this.afterAnimation = afterAnimation
    }

    fun setClickListener(clickListener: () -> Unit = { dismiss() }) {
        this.clickListener = clickListener
    }

    fun startHighlightViewAnimation(targetView: View, duration: Long = 400, size: Float = 6f) {
        val highlightImageView = ImageView(requireContext())

        highlightImageView.id = View.generateViewId()
        highlightImageView.setImageResource(R.drawable.ic_circle)


        // calculate status bar height
        val rec = Rect()
        val window = requireActivity().window
        window.decorView.getWindowVisibleDisplayFrame(rec)
        val statusBarHeight = rec.top

        // target view location and size
        val width = targetView.width
        val height = targetView.height
        val a: IntArray = intArrayOf(0, 0)
        targetView.getLocationOnScreen(a)

        // set size of view using a multiplier to make it bigger
        val params = RelativeLayout.LayoutParams(
            width + (width / size).toInt(),
            height + (height / size).toInt()
        )

        // set margins to position on top of target view
        params.leftMargin = a[0] - ((width / size) / 2).toInt()
        params.topMargin = (a[1] - statusBarHeight) - ((width / size) / 2).toInt()

        // add view
        dialogContainer.addView(highlightImageView, params)

        // animate
        val fadeInAnimation = ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        fadeInAnimation.duration = duration
        fadeInAnimation.fillAfter = true
        fadeInAnimation.interpolator = OvershootInterpolator(3f)
        highlightImageView.startAnimation(fadeInAnimation)
    }

    private suspend fun startTypingAnimation(inputText: CharSequence, afterAnimation: () -> Unit = {}) {
        withContext(Dispatchers.Main) {
            launch {
                inputText.mapIndexed { index, _ ->
                    dialogText.text = inputText.subSequence(0, index)
                    delay(typingDelayInMs)
                }
                afterAnimation()
            }
        }
    }
}