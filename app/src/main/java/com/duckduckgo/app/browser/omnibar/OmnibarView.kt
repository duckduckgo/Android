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
import com.duckduckgo.app.browser.omnibar.Omnibar.Action
import com.duckduckgo.app.browser.omnibar.Omnibar.Content
import com.duckduckgo.app.browser.omnibar.Omnibar.Event
import com.duckduckgo.app.browser.omnibar.Omnibar.Event.PageLoading
import com.duckduckgo.app.browser.omnibar.Omnibar.Event.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.OmnibarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.common.ui.view.hide
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

interface Omnibar {

    // setActionListener?
    fun onAction(actionHandler: (Action) -> Unit)

    // setContentListener?
    fun onContent(contentHandler: (Content) -> Unit)

    fun decorate(event: Event)

    fun enableScrolling()
    fun disableScrolling()

    sealed class Action {
        data class onUrlRequested(val url: String) : Action()
        data class onMenuItemPressed(val menu: MenuItem) : Action()
    }

    sealed class MenuItem {
        object Refresh : MenuItem()
    }

    sealed class Content {
        data class Suggestions(val list: List<String>) : Content()
        object FocusedView : Content()
    }

    sealed class Event {
        data class PrivacyShieldChanged(val privacyShield: PrivacyShield) : Event()
        data class PageLoading(val loadingState: LoadingViewState) : Event()
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
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[OmnibarViewModel::class.java]
    }

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
    }

    private fun render(viewState: ViewState) {
        renderTabIcon(viewState.tabs)
        renderPrivacyShield(viewState.privacyShield)
        renderLoadingState(viewState.loadingState)
    }

    override fun onAction(actionHandler: (Action) -> Unit) {
    }

    override fun onContent(contentHandler: (Content) -> Unit) {
    }

    override fun decorate(event: Event) {
        when (event) {
            is PrivacyShieldChanged -> renderPrivacyShield(event.privacyShield)
            is PageLoading -> viewModel.onNewLoadingState(event.loadingState)
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
    }

    private fun renderLoadingState(loadingState: LoadingViewState) {
        binding.pageLoadingIndicator.apply {
            if (loadingState.isLoading) show()
            smoothProgressAnimator.onNewProgress(loadingState.progress) { if (!loadingState.isLoading) hide() }
        }
        if (loadingState.privacyOn) {
            // if (lastSeenOmnibarViewState?.isEditing == true) {
            //     cancelTrackersAnimation()
            // }

            if (loadingState.progress == MAX_PROGRESS) {
                createTrackersAnimation()
            }
        }
    }

    private fun cancelTrackersAnimation() {
        animatorHelper.cancelAnimations(hideOnAnimationViews())
    }

    private fun createTrackersAnimation() {
    }

    override fun enableScrolling() {
        updateScrollFlag(SCROLL_FLAG_SCROLL or SCROLL_FLAG_SNAP or SCROLL_FLAG_ENTER_ALWAYS, binding.toolbarContainer)
    }

    override fun disableScrolling() {
        updateScrollFlag(0, binding.toolbarContainer)
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
}
