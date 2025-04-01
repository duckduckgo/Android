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
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.ExperimentTrackersCountAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.common.ui.view.fade
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlin.math.abs

private const val TOOLBAR_VISIBLE_THRESHOLD = 0.9f

@InjectWith(FragmentScope::class)
class DelightfulOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val minibar: View by lazy { findViewById(R.id.minibar) }
    private val minibarShield: ImageView by lazy { findViewById(R.id.minibarShield) }
    private val minibarText: DaxTextView by lazy { findViewById(R.id.domainText) }
    private val trackersText: DaxTextView by lazy { findViewById(R.id.trackersText) }

    private var targetHeight: Int = 0
    private var currentHeight: Int = 0
    private var targetToolbarAlpha: Float = 1f
    private var currentToolbarAlpha: Float = 1f
    private var targetTextAlpha: Float = 0f
    private var currentTextAlpha: Float = 0f

    @Inject
    lateinit var experimentTrackersCountAnimationHelper: ExperimentTrackersCountAnimationHelper

    @Inject
    lateinit var duckDuckGoUrlDetector: DuckDuckGoUrlDetector

    init {
        val attr =
            context.theme.obtainStyledAttributes(attrs, R.styleable.DelightfulOmnibarLayout, defStyle, 0)
        omnibarPosition =
            OmnibarPosition.entries[attr.getInt(R.styleable.DelightfulOmnibarLayout_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_delightful_omnibar_bottom
        } else {
            R.layout.view_delightful_omnibar
        }
        inflate(context, layout, this)

        minibar.setOnClickListener {
            revealToolbar()
        }

        AndroidSupportInjection.inject(this)
    }

    override fun render(viewState: ViewState) {
        super.render(viewState)

        experimentTrackersCountAnimationHelper.animate(trackersText, viewState.trackersBlocked, viewState.previouslyTrackersBlocked)
        minibarText.text = viewState.url.extractDomain()
        if (duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(viewState.url)) {
            minibarShield.hide()
        } else if (viewState.privacyShield == PROTECTED) {
            minibarShield.setImageResource(R.drawable.ic_shield_exploration)
            minibarShield.show()
        } else {
            minibarShield.setImageResource(R.drawable.ic_shield_exploration_unprotected)
            minibarShield.show()
        }
    }

    fun resetTransitionDelayed() {
        postDelayed(delayInMillis = 100) {
            revealToolbar()
        }
    }

    fun onScrollChanged(
        scrollableView: View,
        scrollY: Int,
        oldScrollY: Int,
    ) {
        if (currentHeight == 0) {
            currentHeight = layoutParams.height
        }

        if (!scrollableView.canScrollVertically(-1)) { // top of the page condition
            revealToolbar()
            return
        } else if (!scrollableView.canScrollVertically(1)) { // bottom of the page condition
            hideToolbar()
            return
        }

        val isScrollingDown = scrollY > oldScrollY
        val scrollDelta = abs(scrollY - oldScrollY)
        val transitionStepRatio = scrollDelta / 76.toPx(context).toFloat()

        // update layout alpha
        if (isScrollingDown) {
            targetToolbarAlpha = 0f
            targetTextAlpha = 1f
        } else {
            targetToolbarAlpha = 1f
            targetTextAlpha = 0f
        }

        val toolbarAlphaDifference = (targetToolbarAlpha - currentToolbarAlpha)
        val toolbarAlphaChange = (toolbarAlphaDifference * transitionStepRatio)
        currentToolbarAlpha += toolbarAlphaChange
        currentToolbarAlpha = currentToolbarAlpha.coerceIn(0f, 1f)
        fadeToolbar(currentToolbarAlpha)

        val textAlphaDifference = (targetTextAlpha - currentTextAlpha)
        val textAlphaChange = (textAlphaDifference * transitionStepRatio)
        currentTextAlpha += textAlphaChange
        currentTextAlpha = currentTextAlpha.coerceIn(0f, 1f)
        fadeMinibar(currentTextAlpha)

        // update layout height
        targetHeight = if (isScrollingDown) {
            minibar.height
        } else {
            toolbar.height
        }
        if (targetHeight != currentHeight) {
            updateLayoutHeight(transitionStepRatio)
        }
    }

    private fun revealToolbar() {
        // update layout alpha
        targetToolbarAlpha = 1f
        targetTextAlpha = 0f
        currentToolbarAlpha = 1f
        currentTextAlpha = 0f
        fadeToolbar(targetToolbarAlpha)
        fadeMinibar(targetTextAlpha)

        // update layout height
        targetHeight = toolbar.height
        currentHeight = toolbar.height
        layoutParams.height = targetHeight
        this.layoutParams = layoutParams
    }

    private fun hideToolbar() {
        // update layout alpha
        targetToolbarAlpha = 0f
        targetTextAlpha = 1f
        currentToolbarAlpha = 0f
        currentTextAlpha = 1f
        fadeToolbar(targetToolbarAlpha)
        fadeMinibar(targetTextAlpha)

        // update layout height
        targetHeight = minibar.height
        currentHeight = minibar.height
        layoutParams.height = targetHeight
        this.layoutParams = layoutParams
    }

    private fun updateLayoutHeight(transitionStepRatio: Float) {
        val heightDifference = targetHeight - currentHeight
        val heightChange = (heightDifference * transitionStepRatio).toInt()
        currentHeight += heightChange
        currentHeight = currentHeight.coerceIn(minibar.height, toolbar.height)
        if (toolbar.alpha > TOOLBAR_VISIBLE_THRESHOLD) {
            currentHeight = toolbar.height
        } else if (minibar.alpha > TOOLBAR_VISIBLE_THRESHOLD) {
            currentHeight = minibar.height
        }
        layoutParams.height = currentHeight
        this.layoutParams = layoutParams
    }

    /**
     * Returns a percentage (0.0 to 1.0) of how much the omnibar has shifted into the minibar.
     */
    fun getShiftRatio(): Float {
        return currentTextAlpha
    }

    private fun fadeToolbar(alpha: Float) {
        toolbar.fade(alpha)
        pageLoadingIndicator.fade(alpha)
        fireIconMenu.fade(alpha)
        tabsMenu.fade(alpha)
        browserMenu.fade(alpha)
        shieldIconExperiment.fade(alpha)
    }

    private fun fadeMinibar(alpha: Float) {
        minibar.fade(alpha)
        minibar.alpha = alpha
        if (alpha == 0f) {
            minibar.isInvisible = false
        } else if (!minibar.isVisible) {
            minibar.isInvisible = true
        }
        minibar.isClickable = alpha > 0f
    }
}
