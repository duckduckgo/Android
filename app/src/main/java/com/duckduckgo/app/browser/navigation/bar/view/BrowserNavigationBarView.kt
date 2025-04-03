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
import androidx.core.view.doOnAttach
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ViewBrowserNavigationBarBinding
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBackButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyBackButtonLongClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyFireButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyForwardButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyMenuButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command.NotifyTabsButtonLongClicked
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.ViewState
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(ViewScope::class)
class BrowserNavigationBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

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

    val popupMenuAnchor: View = binding.menuButton

    var browserNavigationBarObserver: BrowserNavigationBarObserver? = null

    fun setCanGoBack(canGoBack: Boolean) {
        doOnAttach {
            viewModel.setCanGoBack(canGoBack)
        }
    }

    fun setCanGoForward(canGoForward: Boolean) {
        doOnAttach {
            viewModel.setCanGoForward(canGoForward)
        }
    }

    fun setCustomTab(isCustomTab: Boolean) {
        doOnAttach {
            viewModel.setCustomTab(isCustomTab)
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

        binding.backArrowButton.setOnClickListener {
            viewModel.onBackButtonClicked()
        }

        binding.backArrowButton.setOnLongClickListener {
            viewModel.onBackButtonLongClicked()
            true
        }

        binding.forwardArrowButton.setOnClickListener {
            viewModel.onForwardButtonClicked()
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

    private fun renderView(viewState: ViewState) {
        binding.root.isVisible = viewState.isVisible

        binding.backArrowIconImageView.setImageResource(
            if (viewState.backArrowButtonEnabled) {
                R.drawable.ic_arrow_left_24e
            } else {
                R.drawable.ic_arrow_left_24e_disabled
            },
        )
        binding.backArrowButton.isEnabled = viewState.backArrowButtonEnabled

        binding.forwardArrowIconImageView.setImageResource(
            if (viewState.forwardArrowButtonEnabled) {
                R.drawable.ic_arrow_right_24e
            } else {
                R.drawable.ic_arrow_right_24e_disabled
            },
        )
        binding.forwardArrowButton.isEnabled = viewState.forwardArrowButtonEnabled

        binding.fireButton.isVisible = viewState.fireButtonVisible
        binding.tabsButton.isVisible = viewState.tabsButtonVisible
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
        }
    }
}
