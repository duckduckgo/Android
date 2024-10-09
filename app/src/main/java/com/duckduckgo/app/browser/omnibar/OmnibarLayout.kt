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
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.MotionEvent
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.NewOmnibarViewModel.Command
import com.duckduckgo.app.browser.omnibar.NewOmnibarViewModel.Command.CancelTrackersAnimation
import com.duckduckgo.app.browser.omnibar.NewOmnibarViewModel.Command.FindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.NewOmnibarViewModel.Command.FindInPageInputDismissed
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.CancelAnimations
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.Outline
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onFindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onItemPressed
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEventListener
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarFocusChangedListener
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    interface ItemPressedListener {
        fun onTabsButtonPressed()
        fun onTabsButtonLongPressed()
        fun onFireButtonPressed(isPulseAnimationPlaying: Boolean)
        fun onBrowserMenuPressed()
        fun onPrivacyShieldPressed()
        fun onClearTextPressed()
    }

    interface FindInPageListener {
        fun onFocusChanged(
            hasFocus: Boolean,
            query: String,
        )

        fun onPreviousSearchItemPressed()
        fun onNextSearchItemPressed()
        fun onClosePressed()
    }

    interface TextListener {
        fun onFocusChanged(
            hasFocus: Boolean,
            query: String,
        )

        fun onBackKeyPressed()
        fun onEnterPressed()
        fun onTouchEvent(event: MotionEvent)
    }

    data class OmnibarTextState(
        val text: String,
        val hasFocus: Boolean,
    )

    sealed class Decoration {
        data class Mode(val viewMode: ViewMode) : Decoration()
        data class LaunchTrackersAnimation(val entities: List<Entity>?) : Decoration()
        data class LaunchCookiesAnimation(val isCosmetic: Boolean) : Decoration()
        data object CancelAnimations : Decoration()
        data class ChangeCustomTabTitle(
            val title: String,
            val domain: String?,
            val showDuckPlayerIcon: Boolean,
        ) : Decoration()
        data class PrivacyShieldChanged(val privacyShield: PrivacyShield) : Decoration()
        data class HighlightOmnibarItem(val item: OmnibarItem) : Decoration()
        data class Outline(val enabled: Boolean) : Decoration()
    }

    private val omnibarPosition: OmnibarPosition

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    private lateinit var pulseAnimation: PulseAnimation

    private var omnibarFocusListener: OmnibarFocusChangedListener? = null
    private var omnibarEventListener: OmnibarEventListener? = null

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

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_new_omnibar_bottom
        } else {
            R.layout.view_new_omnibar
        }
        inflate(context, layout, this)
    }

    private fun omnibarViews(): List<View> = listOf(
        clearTextButton,
        omnibarTextInput,
        searchIcon,
    )

    private var coroutineScope: CoroutineScope? = null

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    private val viewModel: OmnibarLayoutViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[OmnibarLayoutViewModel::class.java]
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        pulseAnimation = PulseAnimation(findViewTreeLifecycleOwner()!!)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)
    }

    private fun render(viewState: ViewState) {
        renderTabIcon(viewState.tabs)
        renderViewMode(viewState)
        if (viewState.hasFocus) {
            setExpanded(viewState.expanded)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FindInPageInputChanged -> {
                omnibarEventListener?.onEvent(onFindInPageInputChanged(command.query))
            }

            FindInPageInputDismissed -> {
            }

            CancelTrackersAnimation -> {
                cancelTrackersAnimation()
            }
        }
    }

    private fun renderTabIcon(tabs: List<TabEntity>) {
        context?.let {
            tabsMenu.count = tabs.count()
            tabsMenu.hasUnread = tabs.firstOrNull { !it.viewed } != null
        }
    }

    private fun renderViewMode(viewState: ViewState) {
        when (viewState.viewMode) {
            is CustomTab -> {
                renderCustomTabMode(viewState, viewState.viewMode)
            }
            else -> {
                renderBrowserMode(viewState)
            }
        }
    }

    private fun renderBrowserMode(viewState: ViewState) {
        renderOutline(viewState.hasFocus)
    }

    private fun renderCustomTabMode(viewState: ViewState, viewMode: ViewMode.CustomTab) {
        configureCustomTabOmnibar(viewMode)
        renderButtons(viewState)
        renderPrivacyShield(viewState.privacyShield, viewState.viewMode)
    }

    fun decorate(decoration: Decoration) {
        when (decoration) {
            is Mode -> {
                viewModel.onViewModeChanged(decoration.viewMode)
            }

            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(decoration.privacyShield)
            }

            is Outline -> {
                viewModel.onOutlineEnabled(decoration.enabled)
            }

            CancelAnimations -> {
                cancelTrackersAnimation()
            }

            is LaunchTrackersAnimation -> {
                startTrackersAnimation(decoration.entities)
            }

            is LaunchCookiesAnimation -> {
                createCookiesAnimation(decoration.isCosmetic)
            }

            is ChangeCustomTabTitle -> {
            }
            is HighlightOmnibarItem -> {
            }
        }
    }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).animateToolbarVisibility(expanded)
        }
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded, animate)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).animateToolbarVisibility(expanded)
        }
    }

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> {
        return when (omnibarPosition) {
            OmnibarPosition.TOP -> TopAppBarBehavior(context)
            OmnibarPosition.BOTTOM -> BottomAppBarBehavior(context, this)
        }
    }

    fun setPrivacyShield(
        isCustomTab: Boolean,
        privacyShield: PrivacyShield,
    ) {
        val animationViewHolder = if (isCustomTab) {
            customTabToolbarContainer.customTabShieldIcon
        } else {
            shieldIcon
        }
        privacyShieldView.setAnimationView(animationViewHolder, privacyShield)
        cancelTrackersAnimation()
    }

    fun renderBrowserViewState(
        viewState: BrowserViewState,
        tabDisplayedInCustomTabScreen: Boolean,
    ) {
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

    fun setScrollingEnabled(enabled: Boolean) {
        if (isAttachedToWindow) {
            if (enabled) {
                omnibarScrolling.enableOmnibarScrolling(toolbarContainer)
            } else {
                omnibarScrolling.disableOmnibarScrolling(toolbarContainer)
            }
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
            setScrollingEnabled(false)
            doOnLayout {
                if (this::pulseAnimation.isInitialized) {
                    pulseAnimation.playOn(targetView)
                }
            }
        } else {
            if (viewState.browserShowing) {
                setScrollingEnabled(true)
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

    private fun createCookiesAnimation(isCosmetic: Boolean) {
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

    private fun cancelTrackersAnimation() {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.cancelAnimations(omnibarViews())
        }
    }

    private fun startTrackersAnimation(events: List<Entity>?) {
        animatorHelper.startTrackersAnimation(
            context = context,
            shieldAnimationView = shieldIcon,
            trackersAnimationView = trackersAnimation,
            omnibarViews = omnibarViews(),
            entities = events,
        )
    }

    fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        smoothProgressAnimator.onNewProgress(newProgress, onAnimationEnd)
    }

    private fun renderButtons(viewState: ViewState) {
        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = viewState.showTabsButton
        fireIconMenu.isVisible = viewState.showFireButton
        spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton
    }

    private fun renderPrivacyShield(
        privacyShield: PrivacyShield,
        viewMode: ViewMode,
    ) {
        val shieldIcon = if (viewMode is ViewMode.Browser) {
            shieldIcon
        } else {
            customTabToolbarContainer.customTabShieldIcon
        }

        privacyShieldView.setAnimationView(shieldIcon, privacyShield)
        cancelTrackersAnimation()
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun configureCustomTabOmnibar(customTab: ViewMode.CustomTab) {
        customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(OmnibarView.OmnibarItem.CustomTabClose))
        }

        customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            omnibarEventListener?.onEvent(onItemPressed(OmnibarView.OmnibarItem.CustomTabPrivacyDashboard))
        }

        omniBarContainer.hide()

        toolbar.background = ColorDrawable(customTab.toolbarColor)
        toolbarContainer.background = ColorDrawable(customTab.toolbarColor)

        customTabToolbarContainer.customTabToolbar.show()

        customTabToolbarContainer.customTabDomain.text = customTab.domain
        customTabToolbarContainer.customTabDomainOnly.text = customTab.domain
        customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateCustomTabBackgroundColor(customTab.toolbarColor)
        customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        customTabToolbarContainer.customTabDomainOnly.setTextColor(foregroundColor)
        customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        browserMenuImageView.setColorFilter(foregroundColor)
    }

    private fun calculateCustomTabBackgroundColor(color: Int): Int {
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
}
