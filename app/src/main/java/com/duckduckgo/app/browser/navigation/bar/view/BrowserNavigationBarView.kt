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

package com.duckduckgo.app.browser.navigation.bar.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.AttachedBehavior
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.databinding.ViewBrowserNavigationBarBinding
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyAutofillButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBackButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBackButtonLongClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBookmarksButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyFireButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyForwardButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyMenuButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyNewTabButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonLongClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.OmnibarView
import com.duckduckgo.app.browser.webview.TopOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.browser.ui.omnibar.OmnibarType
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.abs

@InjectWith(ViewScope::class)
class BrowserNavigationBarView @JvmOverloads constructor(
    private val context: Context,
    private val attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), AttachedBehavior {

    @Inject
    lateinit var onboardingDesignExperimentManager: OnboardingDesignExperimentManager

    override fun setVisibility(visibility: Int) {
        val isVisibilityUpdated = this.visibility != visibility

        super.setVisibility(visibility)

        /**
         * This notifies all view behaviors that depend on the [BrowserNavigationBarView] to recalculate whenever the bar's visibility changes,
         * for example, we require that in [TopOmnibarBrowserContainerLayoutBehavior] to remove the bottom inset when navigation bar disappears.
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
        PulseAnimation(lifecycleOwner, onboardingDesignExperimentManager)
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        findViewTreeLifecycleOwner()?.lifecycle?.removeObserver(viewModel)
        conflatedCommandsJob.cancel()
        conflatedStateJob.cancel()
    }

    override fun getBehavior(): Behavior<*> {
        return BottomViewBehavior(context, attrs)
    }

    private fun renderView(viewState: ViewState) {
        binding.root.isVisible = viewState.isVisible

        binding.newTabButton.isVisible = viewState.newTabButtonVisible
        binding.autofillButton.isVisible = viewState.autofillButtonVisible
        binding.bookmarksButton.isVisible = viewState.bookmarksButtonVisible
        binding.fireButton.isVisible = viewState.fireButtonVisible
        binding.tabsButton.isVisible = viewState.tabsButtonVisible
        binding.tabsButton.count = viewState.tabsCount
        binding.tabsButton.hasUnread = viewState.hasUnreadTabs

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
        }
    }

    private fun renderFireButtonPulseAnimation(enabled: Boolean) {
        if (enabled) {
            if (!pulseAnimation.isActive) {
                doOnLayout {
                    pulseAnimation.playOn(targetView = binding.fireIconImageView)
                }
            }
        } else {
            pulseAnimation.stop()
        }
    }

    enum class ViewMode {
        NewTab,
        Browser,
    }

    /**
     * Behavior that offsets the navigation bar proportionally to the offset of the top omnibar.
     *
     * This practically applies only when paired with the top omnibar because if the bottom omnibar is used, it comes with the navigation bar embedded.
     */
    private class BottomViewBehavior(
        context: Context,
        attrs: AttributeSet?,
    ) : Behavior<View>(context, attrs) {
        override fun layoutDependsOn(
            parent: CoordinatorLayout,
            child: View,
            dependency: View,
        ): Boolean {
            return dependency is OmnibarView && dependency.omnibarType != OmnibarType.SINGLE_BOTTOM
        }

        override fun onDependentViewChanged(
            parent: CoordinatorLayout,
            child: View,
            dependency: View,
        ): Boolean {
            if (dependency is OmnibarView && dependency.omnibarType != OmnibarType.SINGLE_BOTTOM) {
                val dependencyOffset = abs(dependency.top)
                val offsetPercentage = dependencyOffset.toFloat() / dependency.measuredHeight.toFloat()
                val childHeight = child.measuredHeight
                val childOffset = childHeight * offsetPercentage
                child.translationY = childOffset
                return true
            }
            return false
        }
    }
}
