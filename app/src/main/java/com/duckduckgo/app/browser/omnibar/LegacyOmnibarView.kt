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
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class LegacyOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle), OmnibarBehaviour {

    private val omnibarPosition: OmnibarPosition

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    private lateinit var pulseAnimation: PulseAnimation

    internal val findInPage by lazy { IncludeFindInPageBinding.bind(findViewById(R.id.findInPage)) }
    internal val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    internal val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    internal val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    internal val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    internal val cookieDummyView: View by lazy { findViewById(R.id.cookieDummyView) }
    internal val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    internal val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    internal val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    internal val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    internal val toolbarContainer: View by lazy { findViewById(R.id.toolbarContainer) }
    internal val customTabToolbarContainer by lazy { IncludeCustomTabToolbarBinding.bind(findViewById(R.id.customTabToolbarContainer)) }
    internal val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    internal val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    internal val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    internal val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    internal val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val spacer: View by lazy { findViewById(R.id.spacer) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }

    private var privacyShield: PrivacyShield? = null

    var isScrollingEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                setExpanded(expanded = true, animate = true)
            }
        }

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_legacy_omnibar_bottom
        } else {
            R.layout.view_legacy_omnibar
        }
        inflate(context, layout, this)
    }

    private fun omnibarViews(): List<View> = listOf(
        clearTextButton,
        omnibarTextInput,
        searchIcon,
    )

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        pulseAnimation = PulseAnimation(findViewTreeLifecycleOwner()!!)

        val deferredPrivacyShield = privacyShield
        if (deferredPrivacyShield != null) {
            setPrivacyShield(false, privacyShield = deferredPrivacyShield)
            privacyShield = null
        }
    }

    override fun setExpanded(expanded: Boolean) {
        safeCall {
            when (omnibarPosition) {
                OmnibarPosition.TOP -> super.setExpanded(expanded)
                OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
            }
        }
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        safeCall {
            when (omnibarPosition) {
                OmnibarPosition.TOP -> super.setExpanded(expanded, animate)
                OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded, animate)
            }
        }
    }

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> {
        return when (omnibarPosition) {
            OmnibarPosition.TOP -> TopAppBarBehavior(context, this)
            OmnibarPosition.BOTTOM -> BottomAppBarBehavior(context, this)
        }
    }

    fun setPrivacyShield(
        isCustomTab: Boolean,
        privacyShield: PrivacyShield,
    ) {
        if (!isAttachedToWindow) {
            this.privacyShield = privacyShield
        }

        safeCall {
            val animationViewHolder = if (isCustomTab) {
                customTabToolbarContainer.customTabShieldIcon
            } else {
                shieldIcon
            }
            privacyShieldView.setAnimationView(animationViewHolder, privacyShield)
            cancelTrackersAnimation()
        }
    }

    private fun safeCall(call: () -> Unit) {
        if (isAttachedToWindow) {
            call()
        }
    }

    fun renderBrowserViewState(
        viewState: BrowserViewState,
        tabDisplayedInCustomTabScreen: Boolean,
    ) {
        safeCall {
            if (viewState.browserShowing) {
                daxIcon.isVisible = viewState.showDaxIcon
                duckPlayerIcon.isVisible = viewState.showDuckPlayerIcon
                shieldIcon.isInvisible =
                    !viewState.showPrivacyShield.isEnabled() || viewState.showDaxIcon || viewState.showDuckPlayerIcon
                clearTextButton.isVisible = viewState.showClearButton
                searchIcon.isVisible = viewState.showSearchIcon
            } else {
                daxIcon.isVisible = false
                duckPlayerIcon.isVisible = false
                shieldIcon.isVisible = false
                clearTextButton.isVisible = viewState.showClearButton
                searchIcon.isVisible = true
            }

            tabsMenu.isVisible = viewState.showTabsButton && !tabDisplayedInCustomTabScreen
            fireIconMenu.isVisible = viewState.fireButton is HighlightableButton.Visible && !tabDisplayedInCustomTabScreen
            browserMenu.isVisible = viewState.showMenuButton is HighlightableButton.Visible

            spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton

            renderPulseAnimation(viewState)
        }
    }

    private fun renderPulseAnimation(viewState: BrowserViewState) {
        val targetView = if (viewState.showMenuButton.isHighlighted()) {
            browserMenuImageView
        } else if (viewState.fireButton.isHighlighted()) {
            fireIconImageView
        } else if (viewState.showPrivacyShield.isHighlighted()) {
            placeholder
        } else {
            null
        }

        // omnibar only scrollable when browser showing and the fire button is not promoted
        if (targetView != null) {
            isScrollingEnabled = false
            doOnLayout {
                if (this::pulseAnimation.isInitialized) {
                    pulseAnimation.playOn(targetView)
                }
            }
        } else {
            if (viewState.browserShowing) {
                isScrollingEnabled = true
            }
            if (this::pulseAnimation.isInitialized) {
                pulseAnimation.stop()
            }
        }
    }

    fun isPulseAnimationPlaying(): Boolean {
        return if (this::pulseAnimation.isInitialized) {
            pulseAnimation.isActive
        } else {
            false
        }
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.createCookiesAnimation(
                context,
                omnibarViews(),
                cookieDummyView,
                cookieAnimation,
                sceneRoot,
                isCosmetic,
            )
        }
    }

    fun cancelTrackersAnimation() {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.cancelAnimations(omnibarViews())
        }
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.startTrackersAnimation(
                context = context,
                shieldAnimationView = shieldIcon,
                trackersAnimationView = trackersAnimation,
                omnibarViews = omnibarViews(),
                entities = events,
            )
        }
    }

    fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        safeCall {
            smoothProgressAnimator.onNewProgress(newProgress, onAnimationEnd)
        }
    }

    override fun measuredHeight(): Int {
        return measuredHeight
    }

    override fun height(): Int {
        return height
    }

    override fun getTranslation(): Float {
        return translationY
    }

    override fun setTranslation(y: Float) {
        translationY = y
    }

    override fun isOmnibarScrollingEnabled(): Boolean {
        return isScrollingEnabled
    }
}
