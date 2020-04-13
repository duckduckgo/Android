/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import com.duckduckgo.app.browser.R

class BottomNavigationBar : LinearLayout {

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {

        val ta: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.BottomNavigationBar)
        val resourceId = ta.getResourceId(R.styleable.BottomNavigationBar_layoutResource, R.layout.layout_browser_bottom_navigation_bar)

        ta.recycle()

        View.inflate(context, resourceId, this)
    }

    fun onItemClicked(view: View, onClick: () -> Unit) {
        view.setOnClickListener {
            onClick()
        }
    }

    fun animateBarVisibility(isVisible: Boolean) {
        val offsetAnimator = ValueAnimator().apply {
            interpolator = DecelerateInterpolator()
            duration = 150L
        }

        offsetAnimator?.addUpdateListener {
            translationY = it.animatedValue as Float
        }

        val targetTranslation = if (isVisible) 0f else height.toFloat()
        offsetAnimator?.setFloatValues(translationY, targetTranslation)
        offsetAnimator?.start()
    }
}