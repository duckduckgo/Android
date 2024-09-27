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
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.OmnibarView.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarView.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarView.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onFindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onItemPressed
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onNewTabRequested
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEvent.onUserSubmittedText
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarEventListener
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarFocusChangedListener
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.FindInPageDismiss
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.FindInPageNextTerm
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.FindInPagePreviousTerm
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.FireButton
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.OverflowItem
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.PrivacyDashboard
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.Tabs
import com.duckduckgo.app.browser.omnibar.OmnibarView.OmnibarItem.VoiceSearch
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange.BrowserStateChanged
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange.FindInPageChanged
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange.OmnibarStateChanged
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange.PageLoading
import com.duckduckgo.app.browser.omnibar.OmnibarView.StateChange.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.CancelTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputDismissed
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.DisplayMode
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.DisplayMode.CustomTab
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.DUCK_PLAYER
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.view.text.TextChangedWatcher
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

interface OmnibarView {

    interface OmnibarFocusChangedListener {
        fun onFocusChange(
            inputText: String,
            focused: Boolean,
        )
        fun onBackKeyPressed()
    }

    interface OmnibarEventListener {
        fun onEvent(event: OmnibarEvent)
    }

    fun setOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener)
    fun setOmnibarEventListener(listener: OmnibarEventListener)
    fun decorate(decoration: Decoration)
    fun reduce(state: StateChange)

    sealed class OmnibarEvent {
        data class onUserSubmittedText(val text: String) : OmnibarEvent()
        data class onUserEnteredText(val text: String) : OmnibarEvent()
        data object onNewTabRequested : OmnibarEvent()
        data class onFindInPageInputChanged(val query: String) : OmnibarEvent()
        data object onFindInPageDismissed : OmnibarEvent()
        data class onItemPressed(val menu: OmnibarItem) : OmnibarEvent()
        data object on
    }

    sealed class OmnibarItem {
        object OverflowItem : OmnibarItem()
        object Tabs : OmnibarItem()
        object FireButton : OmnibarItem()
        object VoiceSearch : OmnibarItem()
        object PrivacyDashboard : OmnibarItem()
        object FindInPagePreviousTerm : OmnibarItem()
        object FindInPageNextTerm : OmnibarItem()
        object FindInPageDismiss : OmnibarItem()
        object CustomTabClose : OmnibarItem()
        object CustomTabPrivacyDashboard : OmnibarItem()
    }

    sealed class Decoration {
        data class LaunchTrackersAnimation(val entities: List<Entity>?) : Decoration()
        data class LaunchCookiesAnimation(val isCosmetic: Boolean) : Decoration()
        data class LaunchCustomTab(
            val toolbarColor: Int,
            val domain: String?,
        ) : Decoration()

        data class ChangeCustomTabTitle(
            val title: String,
            val domain: String?,
        ) : Decoration()

        data class HighlightOmnibarItem(val item: OmnibarItem) : Decoration()
    }

    sealed class StateChange {
        data class PageLoading(val loadingState: LoadingViewState) : StateChange()
        data class BrowserStateChanged(val browserState: BrowserState) : StateChange()
        data class FindInPageChanged(val findInPageState: FindInPageViewState) : StateChange()
        data class OmnibarStateChanged(val omnibarState: OmnibarViewState) : StateChange()
        data class PrivacyShieldChanged(val privacyShield: PrivacyShield) : StateChange()
    }
}

@InjectWith(FragmentScope::class)
class NewOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle), OmnibarView {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    private var coroutineScope: CoroutineScope? = null

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    private val viewModel: OmnibarViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[OmnibarViewModel::class.java]
    }

    private var omnibarFocusListener: OmnibarFocusChangedListener? = null
    private var omnibarEventListener: OmnibarEventListener? = null
    private var decoration: Decoration? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()
    private val omnibarPosition: OmnibarPosition

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
    internal val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val spacer: View by lazy { findViewById(R.id.spacer) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }

    private fun hideOnAnimationViews(): List<View> = listOf(
        clearTextButton,
        omnibarTextInput,
        searchIcon,
    )

    private lateinit var pulseAnimation: PulseAnimation

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.NewOmnibarView, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.NewOmnibarView_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_new_omnibar_bottom
        } else {
            R.layout.view_new_omnibar
        }
        inflate(context, layout, this)
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel).also {
            pulseAnimation = PulseAnimation(findViewTreeLifecycleOwner()!!)
        }

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)

        configureListeners()

        Timber.d("Omnibar: onAttached decoration $decoration stateBuffer $stateBuffer")
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

    @SuppressLint("ClickableViewAccessibility")
    private fun configureListeners() {
        omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->
                viewModel.onOmnibarFocusChanged(hasFocus, omnibarTextInput.text.toString())
                omnibarFocusListener?.onFocusChange(
                    omnibarTextInput.text.toString(),
                    hasFocus,
                )
            }

        omnibarTextInput.replaceTextChangedListener(
            textWatcher = object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    // does this generate double calls to the bar?
                    omnibarEventListener?.onEvent(OmnibarView.OmnibarEvent.onUserEnteredText(omnibarTextInput.text.toString()))
                }
            },
        )

        omnibarTextInput.setOnTouchListener { _, event ->
            viewModel.onUserTouchedOmnibarTextInput(event.action)
            false
        }

        omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                viewModel.onBackKeyPressed()
                omnibarFocusListener?.onBackKeyPressed()
                return false
            }
        }

        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    viewModel.onEnterKeyPressed()
                    val query = omnibarTextInput.text.toString()
                    omnibarEventListener?.onEvent(onUserSubmittedText(query))
                    return@OnEditorActionListener true
                }
                false
            },
        )

        clearTextButton.setOnClickListener {
            viewModel.onClearTextButtonPressed()
        }

        fireIconMenu.setOnClickListener {
            viewModel.onFireButtonPressed(pulseAnimation.isActive)
            omnibarEventListener?.onEvent(onItemPressed(FireButton))
        }

        browserMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(OverflowItem))
        }

        voiceSearchButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(VoiceSearch))
        }

        tabsMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(Tabs))
        }

        tabsMenu.setOnLongClickListener {
            omnibarEventListener?.onEvent(onNewTabRequested)
            return@setOnLongClickListener true
        }

        shieldIcon.setOnClickListener {
            viewModel.onPrivacyDashboardPressed()
            omnibarEventListener?.onEvent(onItemPressed(PrivacyDashboard))
        }

        findInPage.previousSearchTermButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPagePreviousTerm))
        }

        findInPage.nextSearchTermButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPageNextTerm))
        }

        findInPage.closeFindInPagePanel.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPageDismiss))
        }

        findInPage.findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onFindInPageFocusChanged(
                hasFocus,
                findInPage.findInPageInput.text.toString(),
            )
        }

        findInPage.findInPageInput.replaceTextChangedListener(
            textWatcher = object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    viewModel.onFindInPageTextChanged(findInPage.findInPageInput.text.toString())
                }
            },
        )
    }

    private fun render(viewState: ViewState) {
        when (viewState.displayMode) {
            OmnibarViewModel.DisplayMode.Browser -> {
                renderBrowserMode(viewState)
            }

            is OmnibarViewModel.DisplayMode.CustomTab -> {
                renderCustomTabMode(viewState, viewState.displayMode)
            }
        }
    }

    private fun renderCustomTabMode(
        viewState: ViewState,
        displayMode: CustomTab,
    ) {
        Timber.d("Omnibar: renderCustomTabMode")
        configureCustomTabOmnibar(displayMode)
        renderButtons(viewState)
        renderPrivacyShield(viewState.privacyShield, viewState.displayMode)
    }

    private fun renderBrowserMode(viewState: ViewState) {
        renderOutline(viewState.hasFocus)
        renderButtons(viewState)
        renderPulseAnimation(viewState)

        renderLoadingState(viewState.loadingState)
        renderLeadingIconState(viewState.leadingIconState)
        renderFindInPageState(viewState.findInPageState)

        if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
            omnibarTextInput.setText(viewState.omnibarText)
        }

        if (viewState.hasFocus) {
            if (viewState.forceExpand) {
                setExpanded(true, true)
            }

            if (viewState.shouldMoveCaretToEnd) {
                omnibarTextInput.setSelection(viewState.omnibarText.length)
            }
        } else {
            renderTabIcon(viewState.tabs)
            renderPrivacyShield(viewState.privacyShield, viewState.displayMode)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FindInPageInputChanged -> {
                omnibarEventListener?.onEvent(onFindInPageInputChanged(command.query))
            }

            FindInPageInputDismissed -> TODO()
            CancelTrackersAnimation -> {
                cancelAnimations()
            }
        }
    }

    override fun setOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener) {
        omnibarFocusListener = listener
    }

    override fun setOmnibarEventListener(listener: OmnibarEventListener) {
        omnibarEventListener = listener
    }

    override fun decorate(decoration: Decoration) {
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            Timber.d("Omnibar: decorate not attached saving $decoration")
            this.decoration = decoration
        }
    }

    private fun decorateDeferred(decoration: Decoration) {
        Timber.d("Omnibar: decorate $decoration")
        when (decoration) {
            is LaunchTrackersAnimation -> {
                animatorHelper.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = shieldIcon,
                    trackersAnimationView = trackersAnimation,
                    omnibarViews = hideOnAnimationViews(),
                    entities = decoration.entities,
                )
            }

            is LaunchCookiesAnimation -> {
                animatorHelper.createCookiesAnimation(
                    context,
                    hideOnAnimationViews(),
                    cookieDummyView,
                    cookieAnimation,
                    sceneRoot,
                    decoration.isCosmetic,
                )
            }

            is Decoration.LaunchCustomTab -> viewModel.onCustomTabEnabled(decoration)
            is Decoration.HighlightOmnibarItem -> {
                viewModel.onOmnibarItemHighlighted(decoration)
            }

            is Decoration.ChangeCustomTabTitle -> {
                updateCustomTabTitle(decoration.title, decoration.domain)
            }
        }
    }

    override fun reduce(state: StateChange) {
        if (isAttachedToWindow) {
            reduceDeferred(state)
        } else {
            Timber.d("Omnibar: reduce not attached saving $state")
            this.stateBuffer.add(state)
        }
    }

    private fun reduceDeferred(state: StateChange) {
        Timber.d("Omnibar: reduce $state")
        when (state) {
            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(state.privacyShield)
            }

            is PageLoading -> {
                viewModel.onNewLoadingState(state.loadingState)
                animateLoadingState(state.loadingState)
            }

            is BrowserStateChanged -> {
                // dax icons are not changed when browserstate changes
                // should be triggered every time we load a url
                viewModel.onBrowserStateChanged(state.browserState)
            }

            is FindInPageChanged -> {
                viewModel.onFindInPageChanged(state.findInPageState)
            }

            is OmnibarStateChanged -> {
                viewModel.onOmnibarStateChanged(
                    state.omnibarState,
                    omnibarTextInput.text.toString(),
                )
            }
        }
    }

    private fun renderOutline(hasFocus: Boolean) {
        if (hasFocus) {
            omniBarContainer.isPressed = true
        } else {
            omnibarTextInput.hideKeyboard()
            omniBarContainer.isPressed = false
        }
    }

    private fun renderTabIcon(tabs: List<TabEntity>) {
        context?.let {
            tabsMenu.count = tabs.count()
            tabsMenu.hasUnread = tabs.firstOrNull { !it.viewed } != null
        }
    }

    private fun renderPrivacyShield(
        privacyShield: PrivacyShield,
        displayMode: DisplayMode,
    ) {
        val shieldIcon = if (displayMode == DisplayMode.Browser) {
            shieldIcon
        } else {
            customTabToolbarContainer.customTabShieldIcon
        }

        privacyShieldView.setAnimationView(shieldIcon, privacyShield)
        cancelAnimations()
    }

    private fun configureCustomTabOmnibar(customTab: CustomTab) {
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

    private fun updateCustomTabTitle(
        title: String,
        domain: String?,
    ) {
        customTabToolbarContainer.customTabTitle.text = title

        domain?.let {
            customTabToolbarContainer.customTabDomain.text = domain
        }

        customTabToolbarContainer.customTabTitle.show()
        customTabToolbarContainer.customTabDomainOnly.hide()
        customTabToolbarContainer.customTabDomain.show()
    }

    private fun animateLoadingState(loadingState: LoadingViewState) {
        pageLoadingIndicator.apply {
            if (loadingState.isLoading) show()
            smoothProgressAnimator.onNewProgress(loadingState.progress) {
                if (!loadingState.isLoading) hide()
            }
        }
    }

    private fun renderLoadingState(loadingState: LoadingViewState) {
        Timber.d("Omnibar: renderLoadingState $loadingState")
        if (loadingState.privacyOn) {
            if (viewModel.viewState.value.hasFocus) {
                cancelAnimations()
            }
        }
    }

    private fun renderButtons(viewState: ViewState) {
        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = viewState.showTabsButton
        fireIconMenu.isVisible = viewState.showFireButton
        spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val targetView = if (viewState.highlightFireButton.isHighlighted()) {
            fireIconImageView
        } else if (viewState.highlightPrivacyShield.isHighlighted()) {
            placeholder
        } else {
            null
        }

        if (targetView != null) {
            // omnibar is scrollable if no pulse animation is being played
            changeScrollingBehaviour(false)
            if (pulseAnimation.isActive) {
                pulseAnimation.stop()
            }
            toolbarContainer.doOnLayout {
                pulseAnimation.playOn(targetView)
            }
        } else {
            changeScrollingBehaviour(true)
            pulseAnimation.stop()
        }
    }

    private fun renderLeadingIconState(iconState: LeadingIconState) {
        when (iconState) {
            SEARCH -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            PRIVACY_SHIELD -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            DAX -> {
                daxIcon.show()
                shieldIcon.gone()
                searchIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            GLOBE -> {
                globeIcon.show()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }

            DUCK_PLAYER -> {
                globeIcon.gone()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.show()
            }
        }
    }

    private fun renderFindInPageState(viewState: FindInPageViewState) {
        if (viewState.visible) {
            if (findInPage.findInPageContainer.visibility != VISIBLE) {
                findInPage.findInPageContainer.show()
                findInPage.findInPageInput.postDelayed(KEYBOARD_DELAY) {
                    findInPage.findInPageInput.showKeyboard()
                }
            }

            if (viewState.showNumberMatches) {
                findInPage.findInPageMatches.text =
                    context.getString(
                        R.string.findInPageMatches,
                        viewState.activeMatchIndex,
                        viewState.numberMatches,
                    )
                findInPage.findInPageMatches.show()
            } else {
                findInPage.findInPageMatches.hide()
            }
        } else {
            if (findInPage.findInPageContainer.visibility != GONE) {
                findInPage.findInPageContainer.gone()
                findInPage.findInPageInput.hideKeyboard()
            }
        }
    }

    private fun cancelAnimations() {
        animatorHelper.cancelAnimations(hideOnAnimationViews())
    }

    private fun changeScrollingBehaviour(enabled: Boolean) {
        if (enabled) {
            updateScrollFlag(
                SCROLL_FLAG_SCROLL or SCROLL_FLAG_SNAP or SCROLL_FLAG_ENTER_ALWAYS,
                toolbarContainer,
            )
        } else {
            updateScrollFlag(0, toolbarContainer)
        }
    }

    private fun updateScrollFlag(
        flags: Int,
        toolbarContainer: View,
    ) {
        val params = toolbarContainer.layoutParams as AppBarLayout.LayoutParams
        params.scrollFlags = flags
        toolbarContainer.layoutParams = params
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: ViewState,
        omnibarInput: String?,
    ) =
        (!viewState.hasFocus || omnibarInput.isNullOrEmpty()) && omnibarTextInput.isDifferent(
            omnibarInput,
        )

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
