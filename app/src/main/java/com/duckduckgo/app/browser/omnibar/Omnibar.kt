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

import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBottomBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.google.android.material.appbar.AppBarLayout

class Omnibar(
    val omnibarPosition: OmnibarPosition,
    binding: FragmentBrowserTabBinding
) {
    private val topOmnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
    private val bottomOmnibar = IncludeOmnibarToolbarBottomBinding.bind(binding.rootView)

    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                topOmnibar.appBarLayout.show()
                bottomOmnibar.bottomAppBarLayout.hide()

                val param: CoordinatorLayout.LayoutParams = binding.browserLayout.layoutParams as CoordinatorLayout.LayoutParams
                param.behavior = AppBarLayout.ScrollingViewBehavior()
            }

            OmnibarPosition.BOTTOM -> {
                topOmnibar.appBarLayout.hide()
                bottomOmnibar.bottomAppBarLayout.show()

                val param: CoordinatorLayout.LayoutParams = binding.browserLayout.layoutParams as CoordinatorLayout.LayoutParams
                param.behavior = null
            }
        }
    }

    val findInPage
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.findInPage
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomFindInPage
        }

    val omnibarTextInput
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.omnibarTextInput
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomOmnibarTextInput
        }

    val tabsMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.tabsMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomTabsMenu
        }

    val fireIconMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.fireIconMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomFireIconMenu
        }

    val browserMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.browserMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomBrowserMenu
        }

    val cookieDummyView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.cookieDummyView
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomCookieDummyView
        }

    val cookieAnimation
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.cookieAnimation
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomCookieAnimation
        }

    val omnibarIconContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.omnibarIconContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomOmnibarIconContainer
        }

    val omniBarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.omniBarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomOmniBarContainer
        }

    val toolbar
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.toolbar
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomToolbar
        }

    val toolbarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.toolbarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomToolbarContainer
        }

    val customTabToolbarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.customTabToolbarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomCustomTabToolbarContainer
        }

    val browserMenuImageView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.browserMenuImageView
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomBrowserMenuImageView
        }

    val shieldIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.shieldIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomShieldIcon
        }

    val appBarLayout
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.appBarLayout
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomAppBarLayout
        }

    val pageLoadingIndicator
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.pageLoadingIndicator
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomPageLoadingIndicator
        }

    val searchIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.searchIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomSearchIcon
        }

    val daxIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.daxIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomDaxIcon
        }

    val clearTextButton
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.clearTextButton
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomClearTextButton
        }

    val fireIconImageView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.fireIconImageView
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomFireIconImageView
        }

    val placeholder
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.placeholder
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomPlaceholder
        }

    val voiceSearchButton
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.voiceSearchButton
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomVoiceSearchButton
        }

    val spacer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.spacer
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomSpacer
        }

    val trackersAnimation
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.trackersAnimation
            OmnibarPosition.BOTTOM -> bottomOmnibar.bottomTrackersAnimation
        }
}
