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
import android.text.Editable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
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
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarTextState
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
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.CancelTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@InjectWith(FragmentScope::class)
class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle), OmnibarBehaviour {

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
        data class HighlightOmnibarItem(
            val fireButton: Boolean,
            val privacyShield: Boolean,
        ) : Decoration()

        data class Outline(val enabled: Boolean) : Decoration()
    }

    sealed class StateChange {
        data class OmnibarStateChange(val omnibarViewState: OmnibarViewState) : StateChange()
        data class LoadingStateChange(
            val loadingViewState: LoadingViewState,
            val onAnimationEnd: (Animator?) -> Unit,
        ) : StateChange()
    }

    private val omnibarPosition: OmnibarPosition

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    private lateinit var pulseAnimation: PulseAnimation

    private var omnibarTextListener: Omnibar.TextListener? = null
    private var omnibarItemPressedListener: Omnibar.ItemPressedListener? = null

    private var decoration: Decoration? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()

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
    internal val customTabToolbarContainer by lazy {
        IncludeCustomTabToolbarBinding.bind(
            findViewById(R.id.customTabToolbarContainer),
        )
    }
    internal val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    internal val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    internal val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    internal val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    internal val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    internal val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val spacer: View by lazy { findViewById(R.id.spacer) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }

    init {
        val attr =
            context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        omnibarPosition =
            OmnibarPosition.entries[attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0)]

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

    var isScrollingEnabled: Boolean
        get() = viewModel.viewState.value.scrollingEnabled
        set(value) {
            viewModel.onOmnibarScrollingEnabledChanged(value)
        }

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

        viewModel.onAttachedToWindow()

        if (decoration != null) {
            decorateDeferred(decoration!!)
            decoration = null
        }

        if (stateBuffer.isNotEmpty()) {
            stateBuffer.forEach {
                reduce(it)
            }
            stateBuffer.clear()
        }
    }

    fun setOmnibarTextListener(textListener: Omnibar.TextListener) {
        omnibarTextListener = textListener

        omnibarTextInput.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus: Boolean ->
                viewModel.onOmnibarFocusChanged(hasFocus, omnibarTextInput.text.toString())
                omnibarTextListener?.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
            }

        omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                viewModel.onBackKeyPressed()
                omnibarTextListener?.onBackKeyPressed()
                return false
            }
        }

        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    viewModel.onEnterKeyPressed()
                    omnibarTextListener?.onEnterPressed()
                    return@OnEditorActionListener true
                }
                false
            },
        )

        omnibarTextInput.setOnTouchListener { _, event ->
            viewModel.onUserTouchedOmnibarTextInput(event.action)
            false
        }

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    if (isAttachedToWindow) {
                        viewModel.onInputStateChanged(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        )
                    }
                    omnibarTextListener?.onOmnibarTextChanged(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }
            },
        )

        omnibarTextInput.showSuggestionsListener = object : ShowSuggestionsListener {
            override fun showSuggestions() {
                omnibarTextListener?.onShowSuggestions(
                    OmnibarTextState(
                        omnibarTextInput.text.toString(),
                        omnibarTextInput.hasFocus(),
                    ),
                )
            }
        }
    }

    fun setOmnibarItemPressedListener(itemPressedListener: Omnibar.ItemPressedListener) {
        omnibarItemPressedListener = itemPressedListener
        tabsMenu.setOnClickListener {
            omnibarItemPressedListener?.onTabsButtonPressed()
        }
        tabsMenu.setOnLongClickListener {
            omnibarItemPressedListener?.onTabsButtonLongPressed()
            return@setOnLongClickListener true
        }
        fireIconMenu.setOnClickListener {
            viewModel.onFireIconPressed(isPulseAnimationPlaying())
            omnibarItemPressedListener?.onFireButtonPressed(isPulseAnimationPlaying())
        }
        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }
        shieldIcon.setOnClickListener {
            viewModel.onPrivacyShieldButtonPressed()
            omnibarItemPressedListener?.onPrivacyShieldPressed()
        }
        clearTextButton.setOnClickListener {
            viewModel.onClearTextButtonPressed()
            omnibarItemPressedListener?.onClearTextPressed()
        }
    }

    private fun render(viewState: ViewState) {
        when (viewState.viewMode) {
            is CustomTab -> {
                renderCustomTabMode(viewState, viewState.viewMode)
            }

            else -> {
                renderBrowserMode(viewState)
            }
        }
        renderPrivacyShield(viewState.privacyShield, viewState.viewMode)
        renderButtons(viewState)
    }

    private fun processCommand(command: OmnibarLayoutViewModel.Command) {
        when (command) {
            CancelTrackersAnimation -> {
                cancelTrackersAnimation()
            }

            is StartTrackersAnimation -> {
                startTrackersAnimation(command.entities)
            }
        }
    }

    private fun renderTabIcon(viewState: ViewState) {
        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabs.count()
            tabsMenu.hasUnread = viewState.tabs.firstOrNull { !it.viewed } != null
        }
    }

    private fun renderLeadingIconState(iconState: OmnibarLayoutViewModel.LeadingIconState) {
        when (iconState) {
            OmnibarLayoutViewModel.LeadingIconState.SEARCH -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DAX -> {
                daxIcon.show()
                shieldIcon.gone()
                searchIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.GLOBE -> {
                globeIcon.show()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DUCK_PLAYER -> {
                globeIcon.gone()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.show()
            }
        }
    }

    private fun renderButtons(viewState: ViewState) {
        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = viewState.showControls
        fireIconMenu.isVisible = viewState.showControls
        browserMenu.isVisible = viewState.showControls
        spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton
    }

    private fun renderBrowserMode(viewState: ViewState) {
        Timber.d("Omnibar: render browserMode $viewState")
        renderOutline(viewState.hasFocus)
        if (viewState.updateOmnibarText) {
            omnibarTextInput.setText(viewState.omnibarText)
        }
        if (viewState.expanded) {
            setExpanded(true, viewState.expandedAnimated)
        }
        if (viewState.shouldMoveCaretToEnd) {
            omnibarTextInput.setSelection(viewState.omnibarText.length)
        }

        isScrollingEnabled = viewState.scrollingEnabled

        renderTabIcon(viewState)
        renderPulseAnimation(viewState)

        renderLeadingIconState(viewState.leadingIconState)
    }

    private fun renderCustomTabMode(
        viewState: ViewState,
        viewMode: ViewMode.CustomTab,
    ) {
        Timber.d("Omnibar: renderCustomTabMode $viewState")
        configureCustomTabOmnibar(viewMode)
    }

    fun decorate(decoration: Decoration) {
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            if (this.decoration == null) {
                Timber.d("Omnibar: decorate not attached saving $decoration")
                this.decoration = decoration
            }
        }
    }

    private fun decorateDeferred(decoration: Decoration) {
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
                viewModel.onAnimationStarted(decoration)
            }

            is LaunchCookiesAnimation -> {
                createCookiesAnimation(decoration.isCosmetic)
            }

            is ChangeCustomTabTitle -> {
                updateCustomTabTitle(decoration)
            }

            is HighlightOmnibarItem -> {
                viewModel.onHighlightItem(decoration)
            }
        }
    }

    fun reduce(stateChange: StateChange) {
        if (isAttachedToWindow) {
            reduceDeferred(stateChange)
        } else {
            Timber.d("Omnibar: reduce not attached saving $stateChange")
            this.stateBuffer.add(stateChange)
        }
    }

    private fun reduceDeferred(stateChange: StateChange) {
        when (stateChange) {
            is StateChange.LoadingStateChange -> {
                viewModel.onExternalStateChange(stateChange)
                onNewProgress(stateChange.loadingViewState.progress, stateChange.onAnimationEnd)
            }

            else -> {
                viewModel.onExternalStateChange(stateChange)
            }
        }
    }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).animateToolbarVisibility(
                expanded,
            )
        }
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded, animate)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).animateToolbarVisibility(
                expanded,
            )
        }
    }

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> {
        return when (omnibarPosition) {
            OmnibarPosition.TOP -> TopAppBarBehavior(context, this)
            OmnibarPosition.BOTTOM -> BottomAppBarBehavior(context, this)
        }
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val targetView = if (viewState.highlightFireButton.isHighlighted()) {
            fireIconImageView
        } else if (viewState.highlightPrivacyShield.isHighlighted()) {
            placeholder
        } else {
            null
        }

        // omnibar only scrollable when browser showing and the fire button is not promoted
        if (targetView != null) {
            if (this::pulseAnimation.isInitialized) {
                if (pulseAnimation.isActive) {
                    pulseAnimation.stop()
                }
                doOnLayout {
                    if (this::pulseAnimation.isInitialized) {
                        pulseAnimation.playOn(targetView)
                    }
                }
            }
        } else {
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

    private fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        smoothProgressAnimator.onNewProgress(newProgress, onAnimationEnd)
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
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun configureCustomTabOmnibar(customTab: ViewMode.CustomTab) {
        if (!customTabToolbarContainer.customTabToolbar.isVisible) {
            customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
                omnibarItemPressedListener?.onCustomTabClosePressed()
            }

            customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
                omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
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
    }

    private fun updateCustomTabTitle(decoration: ChangeCustomTabTitle) {
        Timber.d("Omnibar: updateCustomTabTitle $decoration")
        customTabToolbarContainer.customTabTitle.text = decoration.title

        decoration.domain?.let {
            customTabToolbarContainer.customTabDomain.text = decoration.domain
        }

        customTabToolbarContainer.customTabTitle.show()
        customTabToolbarContainer.customTabDomainOnly.hide()
        customTabToolbarContainer.customTabDomain.show()
        customTabToolbarContainer.customTabShieldIcon.isInvisible = decoration.showDuckPlayerIcon
        customTabToolbarContainer.customTabDuckPlayerIcon.isVisible = decoration.showDuckPlayerIcon
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
