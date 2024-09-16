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

package com.duckduckgo.app.browser.omnibar

import android.animation.Animator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.databinding.ViewLegacyOmnibarBinding
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class LegacyOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    private val binding: ViewLegacyOmnibarBinding by viewBinding()

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    val findInPage
        get() = binding.findInPage

    val omnibarTextInput
        get() = binding.omnibarTextInput

    val tabsMenu
        get() = binding.tabsMenu

    val fireIconMenu
        get() = binding.fireIconMenu

    val browserMenu
        get() = binding.browserMenu

    val cookieDummyView
        get() = binding.cookieDummyView

    val cookieAnimation
        get() = binding.cookieAnimation

    val sceneRoot
        get() = binding.sceneRoot

    val omniBarContainer
        get() = binding.omniBarContainer

    val toolbar
        get() = binding.toolbar

    val toolbarContainer
        get() = binding.toolbarContainer

    val customTabToolbarContainer
        get() = binding.customTabToolbarContainer

    val browserMenuImageView
        get() = binding.browserMenuImageView

    val shieldIcon
        get() = binding.shieldIcon

    val pageLoadingIndicator
        get() = binding.pageLoadingIndicator

    val searchIcon
        get() = binding.searchIcon

    val daxIcon
        get() = binding.daxIcon

    val clearTextButton
        get() = binding.clearTextButton

    val fireIconImageView
        get() = binding.fireIconImageView

    val placeholder
        get() = binding.placeholder

    val voiceSearchButton
        get() = binding.voiceSearchButton

    val spacer
        get() = binding.spacer

    val trackersAnimation
        get() = binding.trackersAnimation

    val duckPlayerIcon
        get() = binding.duckPlayerIcon

    private fun omnibarViews(): List<View> = listOf(
        binding.clearTextButton,
        binding.omnibarTextInput,
        binding.searchIcon,
    )

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(binding.pageLoadingIndicator) }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        smoothProgressAnimator.onNewProgress(newProgress, onAnimationEnd)
    }

    fun addTextChangedListeners(
        findInPageTextWatcher: TextChangedWatcher,
        omnibarInputTextWatcher: TextChangedWatcher,
        showSuggestionsListener: ShowSuggestionsListener,
    ) {
        findInPage.findInPageInput.replaceTextChangedListener(findInPageTextWatcher)
        binding.omnibarTextInput.replaceTextChangedListener(omnibarInputTextWatcher)
        binding.omnibarTextInput.showSuggestionsListener = showSuggestionsListener
    }

    fun setScrollingEnabled(enabled: Boolean) {
        if (enabled) {
            omnibarScrolling.enableOmnibarScrolling(binding.toolbarContainer)
        } else {
            omnibarScrolling.disableOmnibarScrolling(binding.toolbarContainer)
        }
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        animatorHelper.createCookiesAnimation(
            context,
            omnibarViews(),
            binding.cookieDummyView,
            binding.cookieAnimation,
            binding.sceneRoot,
            isCosmetic,
        )
    }

    fun cancelTrackersAnimation() {
        animatorHelper.cancelAnimations(omnibarViews())
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        animatorHelper.startTrackersAnimation(
            context = context,
            shieldAnimationView = binding.shieldIcon,
            trackersAnimationView = binding.trackersAnimation,
            omnibarViews = omnibarViews(),
            entities = events,
        )
    }

    fun setPrivacyShield(isCustomTab: Boolean, privacyShield: PrivacyShield) {
        val animationViewHolder = if (isCustomTab) {
            binding.customTabToolbarContainer.customTabShieldIcon
        } else {
            binding.shieldIcon
        }
        privacyShieldView.setAnimationView(animationViewHolder, privacyShield)
    }

    fun configureCustomTab(
        customTabToolbarColor: Int,
        customTabDomainText: String?,
        onTabClosePressed: () -> Unit,
        onPrivacyShieldPressed: () -> Unit,
    ) {
        binding.omniBarContainer.hide()
        binding.fireIconMenu.hide()
        binding.tabsMenu.hide()

        binding.toolbar.background = ColorDrawable(customTabToolbarColor)
        binding.toolbarContainer.background = ColorDrawable(customTabToolbarColor)

        binding.customTabToolbarContainer.customTabToolbar.show()

        binding.customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            onTabClosePressed()
        }

        binding.customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            onPrivacyShieldPressed()
        }

        binding.customTabToolbarContainer.customTabDomain.text = customTabDomainText
        binding.customTabToolbarContainer.customTabDomainOnly.text = customTabDomainText
        binding.customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateBlackOrWhite(customTabToolbarColor)
        binding.customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        binding.customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        binding.customTabToolbarContainer.customTabDomainOnly.setTextColor(
            foregroundColor,
        )
        binding.customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        binding.browserMenuImageView.setColorFilter(foregroundColor)
    }

    private fun calculateBlackOrWhite(color: Int): Int {
        // Handle the case where we did not receive a color.
        if (color == 0) {
            return if ((context as DuckDuckGoActivity).isDarkThemeEnabled()) Color.WHITE else Color.BLACK
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return Color.BLACK
        }
        val greyValue =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
        return if (greyValue < 186) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
    ) {
        binding.customTabToolbarContainer.customTabTitle.text = title

        val redirectedDomain = url?.extractDomain()
        redirectedDomain?.let {
            binding.customTabToolbarContainer.customTabDomain.text = redirectedDomain
        }

        binding.customTabToolbarContainer.customTabTitle.show()
        binding.customTabToolbarContainer.customTabDomainOnly.hide()
        binding.customTabToolbarContainer.customTabDomain.show()
    }
}

private fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
    removeTextChangedListener(textWatcher)
    addTextChangedListener(textWatcher)
}
