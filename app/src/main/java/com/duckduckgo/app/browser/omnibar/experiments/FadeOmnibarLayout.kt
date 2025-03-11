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

package com.duckduckgo.app.browser.omnibar.experiments

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.fade
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection
import kotlin.ranges.coerceIn

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val minibar: View by lazy { findViewById(R.id.minibar) }
    private val minibarText: DaxTextView by lazy { findViewById(R.id.minibarText) }

    private var previousScrollY = 0
    private val scrollThreshold = 200
    private var targetHeight: Int = 0
    private var currentHeight: Int = 0
    private var targetToolbarAlpha: Float = 1f
    private var currentToolbarAlpha: Float = 1f
    private var targetTextAlpha: Float = 0f
    private var currentTextAlpha: Float = 0f

    init {
        val attr =
            context.theme.obtainStyledAttributes(attrs, R.styleable.FadeOmnibarLayout, defStyle, 0)
        omnibarPosition =
            OmnibarPosition.entries[attr.getInt(R.styleable.FadeOmnibarLayout_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_fade_omnibar_bottom
        } else {
            R.layout.view_fade_omnibar
        }
        inflate(context, layout, this)

        minibar.setOnClickListener { }

        AndroidSupportInjection.inject(this)
    }

    override fun render(viewState: ViewState) {
        super.render(viewState)

        minibarText.text = viewState.url.extractDomain()
    }

    fun onScrollChanged(scrollY: Int) {
        val isScrollingDown = scrollY > previousScrollY
        previousScrollY = scrollY

        val scrollRatio = (scrollY.toFloat() / scrollThreshold).coerceIn(0f, 1f)

        if (isScrollingDown) {
            targetToolbarAlpha = 0f
            targetTextAlpha = 1f
            targetHeight = minibar.height
        } else {
            targetToolbarAlpha = 1f
            targetTextAlpha = 0f
            targetHeight = toolbar.height
        }

        // top of the page condition
        if (scrollY == 0) {
            targetToolbarAlpha = 1f
            targetTextAlpha = 0f
            targetHeight = toolbar.height
            currentToolbarAlpha = 1f
            currentTextAlpha = 0f
        }

        // smooth alpha transition
        val toolbarAlphaDifference = (targetToolbarAlpha - currentToolbarAlpha)
        val toolbarAlphaChange = (toolbarAlphaDifference * 0.1f)
        currentToolbarAlpha += toolbarAlphaChange
        fadeToolbar(currentToolbarAlpha)

        val textAlphaDifference = (targetTextAlpha - currentTextAlpha)
        val textAlphaChange = (textAlphaDifference * 0.1f)
        currentTextAlpha += textAlphaChange
        fadeMinibar(currentTextAlpha)

        // Calculate the new height for the AppBarLayout
        val layoutParams = layoutParams

        if (currentHeight == 0) {
            currentHeight = layoutParams.height
        }

        if (targetHeight != currentHeight) {
            val heightDifference = targetHeight - currentHeight
            val heightChange = (heightDifference * 0.1f).toInt() // Adjust the factor (0.1f) for faster/slower change
            val newHeight = currentHeight + heightChange
            currentHeight = newHeight
            layoutParams.height = newHeight
            this.layoutParams = layoutParams
        }

        // val scrollRatio = (scrollY.toFloat() / scrollThreshold).coerceIn(0f, 1f)
        // fadeToolbar(1f - scrollRatio)
        // fadeMinibar(scrollRatio)
        //
        // // Calculate the new height for the AppBarLayout
        // val toolbarHeight = toolbar.height
        // val textHeight = minibar.height
        // val newHeight = (toolbarHeight * (1f - scrollRatio) + textHeight * scrollRatio).toInt()
        //
        // // Update the AppBarLayout's height
        // val layoutParams = layoutParams
        // layoutParams.height = newHeight
        // this.layoutParams = layoutParams
    }

    private fun fadeToolbar(alpha: Float) {
        toolbar.fade(alpha)
        pageLoadingIndicator.fade(alpha)
        fireIconMenu.fade(alpha)
        tabsMenu.fade(alpha)
        browserMenu.fade(alpha)
    }

    private fun fadeMinibar(alpha: Float) {
        minibar.fade(alpha)
        minibar.alpha = alpha
        if (alpha == 0f) {
            minibar.isVisible = false
        } else if (!minibar.isVisible) {
            minibar.isVisible = true
        }
    }
}
