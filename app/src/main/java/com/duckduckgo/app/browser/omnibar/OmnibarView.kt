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
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.ViewOmnibarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.BrowserStateChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.PageLoading
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.Scrolling
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarEvent
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarFocusChangedListener
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.BrowserState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.DAX
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.GLOBE
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.LeadingIconState.SEARCH
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserLottieTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
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

    fun onOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener)

    interface OmnibarFocusChangedListener {
        fun onFocusChange(
            focused: Boolean,
            inputText: String,
        )
    }

    // setEventListener?
    fun onOmnibarEvent(eventHandler: (OmnibarEvent) -> Unit)

    fun decorate(decoration: Decoration)

    sealed class OmnibarEvent {
        data class onUrlRequested(val url: String) : OmnibarEvent()
        data class onMenuItemPressed(val menu: MenuItem) : OmnibarEvent()
        data class Suggestions(val list: List<String>) : OmnibarEvent()
    }

    sealed class MenuItem {
        object Refresh : MenuItem()
    }

    sealed class Decoration {
        data class PrivacyShieldChanged(val privacyShield: PrivacyShield) : Decoration()
        data class PageLoading(val loadingState: LoadingViewState) : Decoration()
        data class Scrolling(val enabled: Boolean) : Decoration()
        data class BrowserStateChanged(val browserState: BrowserState) : Decoration()
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
    }

    private fun render(viewState: ViewState) {
        Timber.d("Omnibar: render $viewState")
        renderOutline(viewState.hasFocus)
        renderButtons(viewState)
        renderLeadingIconState(viewState.leadingIconState)
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

    override fun onOmnibarFocusChangeListener(listener: OmnibarFocusChangedListener) {
        omnibarFocusListener = listener
    }

    override fun onOmnibarEvent(eventHandler: (OmnibarEvent) -> Unit) {
    }

    override fun decorate(decoration: Decoration) {
        Timber.d("Omnibar: decorate $decoration")
        when (decoration) {
            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(decoration.privacyShield)
            }
            is PageLoading -> {
                renderLoadingState(decoration.loadingState)
            }
            is Scrolling -> changeScrollingBehaviour(decoration.enabled)
            is BrowserStateChanged -> {
                // dax icons are not changed when browserstate changes
                // should be triggered every time we load a url
                viewModel.onBrowserStateChanged(decoration.browserState)
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
        Timber.d("Omnibar: renderTabIcon ${tabs.count()}")
        context?.let {
            tabsButton.count = tabs.count()
            tabsButton.hasUnread = tabs.firstOrNull { !it.viewed } != null
        }
    }

    private fun renderPrivacyShield(privacyShield: PrivacyShield) {
        Timber.d("Omnibar: renderPrivacyShield $privacyShield")
        privacyShieldView.setAnimationView(binding.shieldIcon, privacyShield)
        cancelTrackersAnimation()
    }

    private fun renderLoadingState(loadingState: LoadingViewState) {
        Timber.d("Omnibar: renderLoadingState $loadingState")
        binding.pageLoadingIndicator.apply {
            if (loadingState.isLoading) show()
            smoothProgressAnimator.onNewProgress(loadingState.progress) {
                if (!loadingState.isLoading) hide()
            }
        }

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
