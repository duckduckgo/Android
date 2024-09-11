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
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.ViewOmnibarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onFindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onItemPressed
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onNewTabRequested
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onUserEnteredText
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEventListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarFocusChangedListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPageDismiss
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPageNextTerm
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPagePreviousTerm
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FireButton
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.OverflowItem
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.PrivacyDashboard
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.Tabs
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.VoiceSearch
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange.BrowserStateChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange.FindInPageChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange.OmnibarStateChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange.PageLoading
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.CancelTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputDismissed
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.DisplayMode
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.DisplayMode.CustomTab
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.global.view.replaceTextChangedListener
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
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

interface Omnibar {

    interface OmnibarFocusChangedListener {
        fun onFocusChange(
            focused: Boolean,
            inputText: String,
        )
    }

    interface OmnibarEventListener {
        fun onEvent(event: OmnibarEvent)
    }

    fun setOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener)
    fun setOmnibarEventListener(listener: OmnibarEventListener)
    fun decorate(decoration: Decoration)
    fun reduce(state: StateChange)

    sealed class OmnibarEvent {
        data class onUserEnteredText(val text: String) : OmnibarEvent()
        data object onNewTabRequested : OmnibarEvent()
        data class onFindInPageInputChanged(val query: String) : OmnibarEvent()
        data object onFindInPageDismissed : OmnibarEvent()
        data class onItemPressed(val menu: OmnibarItem) : OmnibarEvent()
        data class Suggestions(val list: List<String>) : OmnibarEvent()
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

@InjectWith(ViewScope::class)
class OmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), Omnibar {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewOmnibarBinding by viewBinding()

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(binding.pageLoadingIndicator) }
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

    private val tabsButton: TabSwitcherButton
        get() = binding.tabsMenu

    private fun hideOnAnimationViews(): List<View> =
        listOf(binding.clearTextButton, binding.omnibarTextInput, binding.searchIcon)

    private lateinit var pulseAnimation: PulseAnimation

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

    private fun configureListeners() {
        binding.omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->

                viewModel.onOmnibarFocusChanged(hasFocus, binding.omnibarTextInput.text.toString())
                omnibarFocusListener?.onFocusChange(
                    hasFocus,
                    binding.omnibarTextInput.text.toString(),
                )
                // viewModel.onOmnibarInputStateChanged(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
                // viewModel.triggerAutocomplete(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
            }

        binding.omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    val query = binding.omnibarTextInput.text.toString()
                    viewModel.onOmnibarInputTextChanged(query)
                    omnibarEventListener?.onEvent(onUserEnteredText(query))
                    return@OnEditorActionListener true
                }
                false
            },
        )

        binding.clearTextButton.setOnClickListener {
            viewModel.onClearTextButtonPressed()
        }

        binding.fireIconMenu.setOnClickListener {
            // needs to add the pixel because of the animation state
            // pixel.fire(
            //     AppPixelName.MENU_ACTION_FIRE_PRESSED.pixelName,
            //     mapOf(FIRE_BUTTON_STATE to pulseAnimation.isActive.toString()),
            // )
            viewModel.onFireButtonPressed()
            omnibarEventListener?.onEvent(onItemPressed(FireButton))
        }

        binding.browserMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(OverflowItem))
        }

        binding.voiceSearchButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(VoiceSearch))
        }

        binding.tabsMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(Tabs))
        }

        binding.tabsMenu.setOnLongClickListener {
            omnibarEventListener?.onEvent(onNewTabRequested)
            return@setOnLongClickListener true
        }

        binding.shieldIcon.setOnClickListener {
            viewModel.onPrivacyDashboardPressed()
            omnibarEventListener?.onEvent(onItemPressed(PrivacyDashboard))
        }

        binding.findInPage.previousSearchTermButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPagePreviousTerm))
        }
        binding.findInPage.nextSearchTermButton.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPageNextTerm))
        }
        binding.findInPage.closeFindInPagePanel.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FindInPageDismiss))
        }

        binding.findInPage.findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            viewModel.onFindInPageFocusChanged(
                hasFocus,
                binding.findInPage.findInPageInput.text.toString(),
            )
        }

        binding.findInPage.findInPageInput.replaceTextChangedListener(
            textWatcher = object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    viewModel.onFindInPageTextChanged(binding.findInPage.findInPageInput.text.toString())
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
        Timber.d("Omnibar: renderBrowserMode")
        renderOutline(viewState.hasFocus)
        renderButtons(viewState)
        renderPulseAnimation(viewState)

        renderLoadingState(viewState.loadingState)
        renderLeadingIconState(viewState.leadingIconState)
        renderFindInPageState(viewState.findInPageState)

        if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
            binding.omnibarTextInput.setText(viewState.omnibarText)
        }

        if (viewState.hasFocus) {
            if (viewState.forceExpand) {
                binding.appBarLayout.setExpanded(true, true)
            }

            if (viewState.shouldMoveCaretToEnd) {
                binding.omnibarTextInput.setSelection(viewState.omnibarText.length)
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
                    shieldAnimationView = binding.shieldIcon,
                    trackersAnimationView = binding.trackersAnimation,
                    omnibarViews = hideOnAnimationViews(),
                    entities = decoration.entities,
                )
            }

            is LaunchCookiesAnimation -> {
                animatorHelper.createCookiesAnimation(
                    context,
                    hideOnAnimationViews(),
                    binding.cookieDummyView,
                    binding.cookieAnimation,
                    binding.sceneRoot,
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
                    binding.omnibarTextInput.text.toString(),
                )
            }
        }
    }

    private fun renderOutline(hasFocus: Boolean) {
        if (hasFocus) {
            binding.omniBarContainer.isPressed = true
        } else {
            binding.omnibarTextInput.hideKeyboard()
            binding.omniBarContainer.isPressed = false
        }
    }

    private fun renderTabIcon(tabs: List<TabEntity>) {
        context?.let {
            tabsButton.count = tabs.count()
            tabsButton.hasUnread = tabs.firstOrNull { !it.viewed } != null
        }
    }

    private fun renderPrivacyShield(
        privacyShield: PrivacyShield,
        displayMode: DisplayMode,
    ) {
        val shieldIcon = if (displayMode == DisplayMode.Browser) {
            binding.shieldIcon
        } else {
            binding.customTabToolbarContainer.customTabShieldIcon
        }

        privacyShieldView.setAnimationView(shieldIcon, privacyShield)
        cancelAnimations()
    }

    private fun configureCustomTabOmnibar(customTab: CustomTab) {
        binding.customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(Omnibar.OmnibarItem.CustomTabClose))
        }

        binding.customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            omnibarEventListener?.onEvent(onItemPressed(Omnibar.OmnibarItem.CustomTabPrivacyDashboard))
        }

        binding.omniBarContainer.hide()

        binding.toolbar.background = ColorDrawable(customTab.toolbarColor)
        binding.toolbarContainer.background = ColorDrawable(customTab.toolbarColor)

        binding.customTabToolbarContainer.customTabToolbar.show()

        binding.customTabToolbarContainer.customTabDomain.text = customTab.domain
        binding.customTabToolbarContainer.customTabDomainOnly.text = customTab.domain
        binding.customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateCustomTabBackgroundColor(customTab.toolbarColor)
        binding.customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        binding.customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        binding.customTabToolbarContainer.customTabDomainOnly.setTextColor(foregroundColor)
        binding.customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        binding.browserMenuImageView.setColorFilter(foregroundColor)
    }

    private fun updateCustomTabTitle(
        title: String,
        domain: String?,
    ) {
        binding.customTabToolbarContainer.customTabTitle.text = title

        domain?.let {
            binding.customTabToolbarContainer.customTabDomain.text = domain
        }

        binding.customTabToolbarContainer.customTabTitle.show()
        binding.customTabToolbarContainer.customTabDomainOnly.hide()
        binding.customTabToolbarContainer.customTabDomain.show()
    }

    private fun animateLoadingState(loadingState: LoadingViewState) {
        binding.pageLoadingIndicator.apply {
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
        binding.clearTextButton.isVisible = viewState.showClearButton
        binding.voiceSearchButton.isVisible = viewState.showVoiceSearch
        binding.tabsMenu.isVisible = viewState.showTabsButton
        binding.fireIconMenu.isVisible = viewState.showFireButton
        binding.spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val targetView = if (viewState.highlightFireButton.isHighlighted()) {
            binding.fireIconImageView
        } else if (viewState.highlightPrivacyShield.isHighlighted()) {
            binding.placeholder
        } else {
            null
        }

        if (targetView != null) {
            // omnibar is scrollable if no pulse animation is being played
            changeScrollingBehaviour(false)
            if (pulseAnimation.isActive) {
                pulseAnimation.stop()
            }
            binding.toolbarContainer.doOnLayout {
                pulseAnimation.playOn(targetView)
            }
        } else {
            changeScrollingBehaviour(true)
            pulseAnimation.stop()
        }
    }

    private fun renderLeadingIconState(iconState: LeadingIconState) {
        Timber.d("Omnibar: iconState $iconState")
        when (iconState) {
            SEARCH -> {
                binding.searchIcon.show()
                binding.shieldIcon.gone()
                binding.daxIcon.gone()
                binding.globeIcon.gone()
            }

            PRIVACY_SHIELD -> {
                binding.shieldIcon.show()
                binding.searchIcon.gone()
                binding.daxIcon.gone()
                binding.globeIcon.gone()
            }

            DAX -> {
                binding.daxIcon.show()
                binding.shieldIcon.gone()
                binding.searchIcon.gone()
                binding.globeIcon.gone()
            }

            GLOBE -> {
                binding.globeIcon.show()
                binding.daxIcon.gone()
                binding.shieldIcon.gone()
                binding.searchIcon.gone()
            }
        }
    }

    private fun renderFindInPageState(viewState: FindInPageViewState) {
        if (viewState.visible) {
            if (binding.findInPage.findInPageContainer.visibility != VISIBLE) {
                binding.findInPage.findInPageContainer.show()
                binding.findInPage.findInPageInput.postDelayed(KEYBOARD_DELAY) {
                    binding.findInPage.findInPageInput.showKeyboard()
                }
            }

            if (viewState.showNumberMatches) {
                binding.findInPage.findInPageMatches.text =
                    context.getString(
                        R.string.findInPageMatches,
                        viewState.activeMatchIndex,
                        viewState.numberMatches,
                    )
                binding.findInPage.findInPageMatches.show()
            } else {
                binding.findInPage.findInPageMatches.hide()
            }
        } else {
            if (binding.findInPage.findInPageContainer.visibility != GONE) {
                binding.findInPage.findInPageContainer.gone()
                binding.findInPage.findInPageInput.hideKeyboard()
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
                binding.toolbarContainer,
            )
        } else {
            updateScrollFlag(0, binding.toolbarContainer)
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
        (!viewState.hasFocus || omnibarInput.isNullOrEmpty()) && binding.omnibarTextInput.isDifferent(
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
