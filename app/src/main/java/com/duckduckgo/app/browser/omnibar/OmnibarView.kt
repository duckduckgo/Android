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
import android.text.Editable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.postDelayed
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.ViewOmnibarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.BrowserStateChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.FindInPageChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.PageLoading
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.Scrolling
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onFindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onItemPressed
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent.onNewTabRequested
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEventListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarFocusChangedListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPageDismiss
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPageNextTerm
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FindInPagePreviousTerm
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.FireButton
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.OverflowItem
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.PrivacyDashboard
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarItem.Tabs
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.Command.FindInPageInputDismissed
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserLottieTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.global.view.replaceTextChangedListener
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.store.AppTheme
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

    fun setOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener)

    interface OmnibarFocusChangedListener {
        fun onFocusChange(
            focused: Boolean,
            inputText: String,
        )
    }

    fun setOmnibarEventListener(listener: OmnibarEventListener)
    interface OmnibarEventListener {
        fun onEvent(event: OmnibarEvent)
    }

    fun decorate(decoration: Decoration)

    sealed class OmnibarEvent {
        data class onUrlRequested(val url: String) : OmnibarEvent()
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
        object PrivacyDashboard : OmnibarItem()
        object FindInPagePreviousTerm : OmnibarItem()
        object FindInPageNextTerm : OmnibarItem()
        object FindInPageDismiss : OmnibarItem()
    }

    sealed class Decoration {
        data class PrivacyShieldChanged(val privacyShield: PrivacyShield) : Decoration()
        data class PageLoading(val loadingState: LoadingViewState) : Decoration()
        data class Scrolling(val enabled: Boolean) : Decoration()
        data class BrowserStateChanged(val browserState: BrowserState) : Decoration()
        data class FindInPageChanged(val findInPageState: FindInPageViewState) : Decoration()
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
    lateinit var appTheme: AppTheme

    private var coroutineScope: CoroutineScope? = null

    private val binding: ViewOmnibarBinding by viewBinding()

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(binding.pageLoadingIndicator) }
    private val viewModel: OmnibarViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[OmnibarViewModel::class.java]
    }

    private var omnibarFocusListener: OmnibarFocusChangedListener? = null
    private var omnibarEventListener: OmnibarEventListener? = null

    private val tabsButton: TabSwitcherButton
        get() = binding.tabsMenu

    private fun hideOnAnimationViews(): List<View> = listOf(binding.clearTextButton, binding.omnibarTextInput, binding.searchIcon)

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        findViewTreeLifecycleOwner()?.lifecycle?.addObserver(viewModel)

        @SuppressLint("NoHardcodedCoroutineDispatcher")
        coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        viewModel.viewState
            .onEach { render(it) }
            .launchIn(coroutineScope!!)

        viewModel.commands()
            .onEach { processCommand(it) }
            .launchIn(coroutineScope!!)

        configureListeners()
    }

    private fun configureListeners() {
        binding.omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->
                viewModel.onOmnibarFocusChanged(hasFocus, binding.omnibarTextInput.text.toString())
                omnibarFocusListener?.onFocusChange(hasFocus, binding.omnibarTextInput.text.toString())
                // viewModel.onOmnibarInputStateChanged(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
                // viewModel.triggerAutocomplete(omnibar.omnibarTextInput.text.toString(), hasFocus, false)
            }

        binding.fireIconMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(FireButton))
        }

        binding.browserMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(OverflowItem))
        }

        binding.tabsMenu.setOnClickListener {
            omnibarEventListener?.onEvent(onItemPressed(Tabs))
        }

        binding.tabsMenu.setOnLongClickListener {
            omnibarEventListener?.onEvent(onNewTabRequested)
            return@setOnLongClickListener true
        }

        binding.shieldIcon.setOnClickListener {
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
            viewModel.onFindInPageFocusChanged(hasFocus, binding.findInPage.findInPageInput.text.toString())
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
        renderOutline(viewState.hasFocus)
        renderButtons(viewState)
        renderLoadingState(viewState.loadingState)
        renderLeadingIconState(viewState.leadingIconState)
        renderFindInPageState(viewState.findInPageState)
        if (viewState.hasFocus) {
            if (viewState.forceExpand) {
                binding.appBarLayout.setExpanded(true, true)
            }

            if (viewState.shouldMoveCaretToEnd) {
                binding.omnibarTextInput.setSelection(viewState.omnibarText.length)
            }
        } else {
            renderTabIcon(viewState.tabs)
            renderPrivacyShield(viewState.privacyShield)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is FindInPageInputChanged -> {
                omnibarEventListener?.onEvent(onFindInPageInputChanged(command.query))
            }

            FindInPageInputDismissed -> TODO()
        }
    }

    override fun setOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener) {
        omnibarFocusListener = listener
    }

    override fun setOmnibarEventListener(listener: OmnibarEventListener) {
        omnibarEventListener = listener
    }

    override fun decorate(decoration: Decoration) {
        Timber.d("Omnibar: decorate $decoration")
        when (decoration) {
            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(decoration.privacyShield)
            }

            is PageLoading -> {
                viewModel.onNewLoadingState(decoration.loadingState)
                animateLoadingState(decoration.loadingState)
            }

            is Scrolling -> changeScrollingBehaviour(decoration.enabled)
            is BrowserStateChanged -> {
                // dax icons are not changed when browserstate changes
                // should be triggered every time we load a url
                viewModel.onBrowserStateChanged(decoration.browserState)
            }

            is FindInPageChanged -> {
                viewModel.onFindInPageChanged(decoration.findInPageState)
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

    private fun renderPrivacyShield(privacyShield: PrivacyShield) {
        privacyShieldView.setAnimationView(binding.shieldIcon, privacyShield)
        cancelTrackersAnimation()
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
                cancelTrackersAnimation()
            }

            if (loadingState.progress == MAX_PROGRESS) {
                createTrackersAnimation()
            }
        }
    }

    private fun renderButtons(viewState: ViewState) {
        if (viewState.hasFocus) {
            cancelTrackersAnimation()
        }

        if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
            binding.omnibarTextInput.setText(viewState.omnibarText)
            if (viewState.forceExpand) {
                binding.appBarLayout.setExpanded(true, true)
            }
            if (viewState.shouldMoveCaretToEnd) {
                binding.omnibarTextInput.setSelection(viewState.omnibarText.length)
            }
        }
    }

    private fun renderLeadingIconState(iconState: LeadingIconState) {
        Timber.d("Omnibar: renderLeadingIconState $iconState")
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
                    context.getString(R.string.findInPageMatches, viewState.activeMatchIndex, viewState.numberMatches)
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

    private fun cancelTrackersAnimation() {
        val animatorHelper = BrowserLottieTrackersAnimatorHelper(appTheme)
        animatorHelper.cancelAnimations(hideOnAnimationViews())
    }

    private fun createTrackersAnimation() {
        val animatorHelper = BrowserLottieTrackersAnimatorHelper(appTheme)
    }

    private fun changeScrollingBehaviour(enabled: Boolean) {
        if (enabled) {
            updateScrollFlag(SCROLL_FLAG_SCROLL or SCROLL_FLAG_SNAP or SCROLL_FLAG_ENTER_ALWAYS, binding.toolbarContainer)
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

    companion object {
        private const val MAX_PROGRESS = 100
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: ViewState,
        omnibarInput: String?,
    ) =
        (!viewState.hasFocus || omnibarInput.isNullOrEmpty()) && binding.omnibarTextInput.isDifferent(omnibarInput)
}
