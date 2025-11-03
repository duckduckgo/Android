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

package com.duckduckgo.app.browser.navigation.bar

import com.duckduckgo.app.browser.BrowserTabFragment
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarObserver
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.Browser
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.keyboardVisibilityFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Helper class that extracts business logic that manages the [BrowserNavigationBarView] from the [BrowserTabFragment].
 *
 * The class needs to be instantiated strictly after the fragment's view has been created,
 * and [onDestroyView] has to be called when the the fragment's view is destroyed.
 * After the view is destroyed, the class becomes no-op and needs to be re-instantiated with a valid view binding.
 */
class BrowserNavigationBarViewIntegration(
    private val lifecycleScope: CoroutineScope,
    browserTabFragmentBinding: FragmentBrowserTabBinding,
    isEnabled: Boolean,
    private val omnibar: Omnibar,
    browserNavigationBarObserver: BrowserNavigationBarObserver,
) {
    val navigationBarView: BrowserNavigationBarView = browserTabFragmentBinding.navigationBar

    private var keyboardVisibilityObserverJob: Job? = null
    private var navigationBarVisibilityChangeJob: Job? = null

    init {
        if (isEnabled) {
            onEnabled()
        } else {
            onDisabled()
        }
        navigationBarView.browserNavigationBarObserver = browserNavigationBarObserver
    }

    fun configureCustomTab() {
        navigationBarView.setCustomTab(isCustomTab = true)
    }

    fun configureBrowserViewMode() {
        navigationBarView.setViewMode(Browser)
    }

    fun configureNewTabViewMode() {
        navigationBarView.setViewMode(NewTab)
    }

    fun configureFireButtonHighlight(highlighted: Boolean) {
        navigationBarView.setFireButtonHighlight(highlighted)
    }

    fun onDestroyView() {
        onDisabled()
    }

    private fun onEnabled() {
        navigationBarView.show()
        // we're hiding the navigation bar when keyboard is shown,
        // to prevent it from being "pushed up" within the coordinator layout
        keyboardVisibilityObserverJob = lifecycleScope.launch {
            omnibar.textInputRootView.keyboardVisibilityFlow().distinctUntilChanged().collect { keyboardVisible ->
                navigationBarVisibilityChangeJob?.cancel()
                if (keyboardVisible) {
                    navigationBarView.gone()
                } else {
                    navigationBarVisibilityChangeJob = launch {
                        delay(BrowserTabFragment.KEYBOARD_DELAY)
                        navigationBarView.show()
                    }
                }
            }
        }
    }

    private fun onDisabled() {
        navigationBarView.gone()
        keyboardVisibilityObserverJob?.cancel()
    }
}
