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

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import android.view.WindowManager
import android.view.Gravity
import android.view.MenuInflater
import android.view.MenuItem
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.android.synthetic.main.sheet_dax_dialog.*
import android.view.animation.AnimationSet
import android.view.animation.AccelerateInterpolator
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


class DaxBottomSheetDialog(val clickOk: () -> Unit, val test: View, val other: View) : DialogFragment(),
    CoroutineScope {

    private val clearJob: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + clearJob

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val window = dialog.window
        val wlp = window?.attributes
        wlp?.gravity = Gravity.BOTTOM
        wlp?.width = WindowManager.LayoutParams.MATCH_PARENT
        wlp?.verticalMargin = 20f
        //wlp?.height= WindowManager.LayoutParams.MATCH_PARENT
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.attributes = wlp

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        primaryCta.setOnClickListener {
            test(test)
        }
        secondaryCta.setOnClickListener {
            test(other)
        }

        val handler = Handler()


        launch {
            dialogText.animate2("Every site has a privacy grade. Tap it to see how I protected your privacy.\n\nWant to get fancy? Try clearing your data by hitting the fire button.") {
                handler.postDelayed(
                    {
                        test(other)
                        test(test)
                    }
                    , 300)
            }
        }
    }

    fun test(targetView: View) {
        Timber.d("MARCOS executed yes! ")
        // val myLayout: LinearLayout = requireActivity().findViewById(R.id.dialogContainer)

        val myButton = ImageView(requireContext())
        myButton.id = View.generateViewId()

        myButton.setImageResource(R.drawable.ic_circle)
        //myButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.midGreen))
        //myButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.midGreen))

//        myButton.layoutParams = params


//        val constraints = ConstraintSet()
//        constraints.clone(dialogContainer)
//
//        constraints.connect(myButton.id, ConstraintSet.START, ConstraintSet.START, test.itemId)
//
//        constraints.connect(myButton.id, ConstraintSet.END, ConstraintSet.END, test.itemId)
//
//        constraints.applyTo(dialogContainer)

        // statusbar height

        val rec = Rect()
        val window = requireActivity().window

        window.decorView.getWindowVisibleDisplayFrame(rec)
        val statusheight = rec.top


        // enf status bar height

        val a: IntArray = intArrayOf(0, 0)
        val r: Rect = Rect()
        targetView.getLocationOnScreen(a)
        val b = targetView.clipBounds
        //  params.leftMargin = (a[0] + (a[0] + targetView.width)) / 2
//        params.topMargin = ((a[1] - statusheight) + ((a[1] - statusheight) + targetView.height)) / 2

        val width = (targetView.width)
        val height = targetView.height // (a[1])// - statusheight) //+ targetView.height
        val params = RelativeLayout.LayoutParams(
            width + (width / 6),
            height + (height / 6)
        )
//        targetView.x = a[0].toFloat()
//        targetView.y = (a[1] - statusheight).toFloat()

        params.leftMargin = a[0] - ((width / 6) / 2)
        params.topMargin = (a[1] - statusheight) - ((width / 6) / 2)

        Timber.d("MARCOS view: ${targetView.id}")
        Timber.d("MARCOS height: ${height}")
        Timber.d("MARCOS width: ${width}")
        Timber.d("MARCOS left: ${params.leftMargin}")
        Timber.d("MARCOS top: ${params.topMargin}")

        dialogContainer.addView(myButton, params)


        val fade_in: ScaleAnimation = ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        fade_in.setDuration(400)     // animation duration in milliseconds
        fade_in.setFillAfter(true)    // If fillAfter is true, the transformation that this animation performed will persist when it is finished.
        fade_in.interpolator = OvershootInterpolator(3f)
        myButton.startAnimation(fade_in)

        val righttopcorner = ImageView(requireContext())
        righttopcorner.id = View.generateViewId()
        righttopcorner.setImageResource(R.drawable.ic_circle)
        righttopcorner.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.cornflowerBlue))
        val params2 = RelativeLayout.LayoutParams(
            20,
            20
        )
        params2.leftMargin = a[0]
        params2.topMargin = (a[1] - statusheight)

        //    dialogContainer.addView(righttopcorner, params2)

        val bottomrightcorner = ImageView(requireContext())
        bottomrightcorner.id = View.generateViewId()
        bottomrightcorner.setImageResource(R.drawable.ic_circle)
        val params3 = RelativeLayout.LayoutParams(
            20,
            20
        )
        params3.leftMargin = a[0] + targetView.width
        params3.topMargin = (a[1] - statusheight) + targetView.height

//        dialogContainer.addView(bottomrightcorner, params3)


//        val rect = test.icon.bounds
//        val v = MarcosTest(requireContext())
//        v.setView(rect)
//
//        test.actionView = v
//
//        v.startMyAnimation()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sheet_dax_dialog, container, false)
        //return super.onCreateView(inflater, container, savedInstanceState)
    }
}