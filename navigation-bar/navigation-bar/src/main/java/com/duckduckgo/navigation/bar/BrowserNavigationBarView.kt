/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.navigation.bar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.PulseAnimation
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyAiChatButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyAutofillButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyBackButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyBackButtonLongClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyBookmarksButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyFireButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyForwardButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyMenuButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyNewTabButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyTabsButtonClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyTabsButtonLongClicked
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.bar.BrowserNavigationBarViewModel.Command.NotifyWebButtonClicked
import com.duckduckgo.navigation.bar.databinding.ViewBrowserNavigationBarBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class BrowserNavigationBarView @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private var showShadows: Boolean = false

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.BrowserNavigationBarView, defStyle, 0)
            .apply {
                showShadows = getBoolean(R.styleable.BrowserNavigationBarView_showShadows, true)
                recycle()
            }
    }

    override fun setVisibility(visibility: Int) {
        val isVisibilityUpdated = this.visibility != visibility

        super.setVisibility(visibility)

        /**
         * This notifies all view behaviors that depend on the [BrowserNavigationBarView] to recalculate whenever the bar's visibility changes,
         * for example, we require that in `TopOmnibarBrowserContainerLayoutBehavior` to remove the bottom inset when navigation bar disappears.
         * The base coordinator behavior doesn't notify dependent views when visibility changes, so we need to do that manually.
         */
        val parent = parent
        if (isVisibilityUpdated && parent is CoordinatorLayout) {
            parent.dispatchDependentViewsChanged(this)
        }
    }

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewBrowserNavigationBarBinding by viewBinding()

    private val viewModel: BrowserNavigationBarViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[BrowserNavigationBarViewModel::class.java]
    }

    private var conflatedCommandsJob: ConflatedJob = ConflatedJob()
    private var conflatedStateJob: ConflatedJob = ConflatedJob()

    private val lifecycleOwner: LifecycleOwner by lazy {
        requireNotNull(findViewTreeLifecycleOwner())
    }

    private val pulseAnimation: PulseAnimation by lazy {
        PulseAnimation(lifecycleOwner)
    }

    val popupMenuAnchor: View = binding.menuButton

    var browserNavigationBarObserver: BrowserNavigationBarObserver? = null

    fun setCustomTab(isCustomTab: Boolean) {
        doOnAttach {
            viewModel.setCustomTab(isCustomTab)
        }
    }

    fun setViewMode(viewMode: ViewMode) {
        doOnAttach {
            viewModel.setViewMode(viewMode)
        }
    }

    fun getViewMode(): ViewMode = viewModel.viewMode.value

    fun setFireButtonHighlight(highlighted: Boolean) {
        doOnAttach {
            viewModel.setFireButtonHighlight(highlighted)
        }
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        val viewTreeLifecycleOwner = findViewTreeLifecycleOwner()!!
        viewTreeLifecycleOwner.lifecycle.addObserver(viewModel)
        val coroutineScope = viewTreeLifecycleOwner.lifecycleScope

        conflatedCommandsJob += viewModel.commands
            .onEach(::processCommands)
            .launchIn(coroutineScope)

        conflatedStateJob += viewModel.viewState
            .onEach(::renderView)
            .launchIn(coroutineScope)

        binding.newTabButton.setOnClickListener {
            viewModel.onNewTabButtonClicked()
        }

        binding.autofillButton.setOnClickListener {
            viewModel.onAutofillButtonClicked()
        }

        binding.bookmarksButton.setOnClickListener {
            viewModel.onBookmarksButtonClicked()
        }

        binding.fireButton.setOnClickListener {
            viewModel.onFireButtonClicked()
        }

        binding.tabsButton.setOnClickListener {
            viewModel.onTabsButtonClicked()
        }

        binding.tabsButton.setOnLongClickListener {
            viewModel.onTabsButtonLongClicked()
            true
        }

        binding.menuButton.setOnClickListener {
            viewModel.onMenuButtonClicked()
        }

        binding.aiChatButton.setOnClickListener {
            viewModel.onAiChatButtonClicked()
        }

        binding.webButton.setOnClickListener {
            viewModel.onWebButtonClicked()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedCommandsJob.cancel()
        conflatedStateJob.cancel()
    }

    private fun renderView(viewState: ViewState) {
        binding.shadowView.isVisible = showShadows
        binding.root.isVisible = viewState.isVisible

        binding.newTabButton.isVisible = viewState.newTabButtonVisible
        binding.autofillButton.isVisible = viewState.autofillButtonVisible
        binding.bookmarksButton.isVisible = viewState.bookmarksButtonVisible
        binding.fireButton.isVisible = viewState.fireButtonVisible
        binding.tabsButton.isVisible = viewState.tabsButtonVisible
        binding.tabsButton.count = viewState.tabsCount
        binding.tabsButton.hasUnread = viewState.hasUnreadTabs
        binding.aiChatButton.isVisible = viewState.aiChatButtonVisible
        binding.webButton.isVisible = viewState.webButtonVisible

        renderFireButtonPulseAnimation(enabled = viewState.fireButtonHighlighted)
    }

    private fun processCommands(command: Command) {
        when (command) {
            NotifyFireButtonClicked -> browserNavigationBarObserver?.onFireButtonClicked()
            NotifyTabsButtonClicked -> browserNavigationBarObserver?.onTabsButtonClicked()
            NotifyTabsButtonLongClicked -> browserNavigationBarObserver?.onTabsButtonLongClicked()
            NotifyMenuButtonClicked -> browserNavigationBarObserver?.onMenuButtonClicked()
            NotifyBackButtonClicked -> browserNavigationBarObserver?.onBackButtonClicked()
            NotifyBackButtonLongClicked -> browserNavigationBarObserver?.onBackButtonLongClicked()
            NotifyForwardButtonClicked -> browserNavigationBarObserver?.onForwardButtonClicked()
            NotifyBookmarksButtonClicked -> browserNavigationBarObserver?.onBookmarksButtonClicked()
            NotifyNewTabButtonClicked -> browserNavigationBarObserver?.onNewTabButtonClicked()
            NotifyAutofillButtonClicked -> browserNavigationBarObserver?.onAutofillButtonClicked()
            NotifyAiChatButtonClicked -> browserNavigationBarObserver?.onAiChatButtonClicked()
            NotifyWebButtonClicked -> browserNavigationBarObserver?.onWebButtonClicked()
        }
    }

    private fun renderFireButtonPulseAnimation(enabled: Boolean) {
        if (enabled) {
            if (!pulseAnimation.isActive) {
                doOnLayout {
                    pulseAnimation.playOn(binding.fireIconImageView, isExperimentAndShieldView = false)
                }
            }
        } else {
            pulseAnimation.stop()
        }
    }

    enum class ViewMode {
        NewTab,
        Browser,
        DuckChat,
    }
}
