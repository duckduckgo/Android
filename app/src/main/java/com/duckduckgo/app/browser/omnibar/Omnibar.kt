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
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition

class Omnibar(
    val omnibarPosition: OmnibarPosition,
    binding: FragmentBrowserTabBinding,
) {
    private val topOmnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
    private val bottomOmnibar = binding.bottomToolbarInclude

    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.rootView.removeView(bottomOmnibar.appBarLayout)
            }

            OmnibarPosition.BOTTOM -> {
                binding.rootView.removeView(topOmnibar.appBarLayout)
                (binding.browserLayout.layoutParams as CoordinatorLayout.LayoutParams).behavior = null
            }
        }
    }

    val findInPage
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.findInPage
            OmnibarPosition.BOTTOM -> bottomOmnibar.findInPage
        }

    val omnibarTextInput
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.omnibarTextInput
            OmnibarPosition.BOTTOM -> bottomOmnibar.omnibarTextInput
        }

    val tabsMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.tabsMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.tabsMenu
        }

    val fireIconMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.fireIconMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.fireIconMenu
        }

    val browserMenu
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.browserMenu
            OmnibarPosition.BOTTOM -> bottomOmnibar.browserMenu
        }

    val cookieDummyView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.cookieDummyView
            OmnibarPosition.BOTTOM -> bottomOmnibar.cookieDummyView
        }

    val cookieAnimation
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.cookieAnimation
            OmnibarPosition.BOTTOM -> bottomOmnibar.cookieAnimation
        }

    val sceneRoot
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.sceneRoot
            OmnibarPosition.BOTTOM -> bottomOmnibar.sceneRoot
        }

    val omniBarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.omniBarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.omniBarContainer
        }

    val toolbar
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.toolbar
            OmnibarPosition.BOTTOM -> bottomOmnibar.toolbar
        }

    val toolbarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.toolbarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.toolbarContainer
        }

    val customTabToolbarContainer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.customTabToolbarContainer
            OmnibarPosition.BOTTOM -> bottomOmnibar.customTabToolbarContainer
        }

    val browserMenuImageView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.browserMenuImageView
            OmnibarPosition.BOTTOM -> bottomOmnibar.browserMenuImageView
        }

    val shieldIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.shieldIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.shieldIcon
        }

    val appBarLayout
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.appBarLayout
            OmnibarPosition.BOTTOM -> bottomOmnibar.appBarLayout
        }

    val pageLoadingIndicator
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.pageLoadingIndicator
            OmnibarPosition.BOTTOM -> bottomOmnibar.pageLoadingIndicator
        }

    val searchIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.searchIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.searchIcon
        }

    val daxIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.daxIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.daxIcon
        }

    val clearTextButton
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.clearTextButton
            OmnibarPosition.BOTTOM -> bottomOmnibar.clearTextButton
        }

    val fireIconImageView
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.fireIconImageView
            OmnibarPosition.BOTTOM -> bottomOmnibar.fireIconImageView
        }

    val placeholder
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.placeholder
            OmnibarPosition.BOTTOM -> bottomOmnibar.placeholder
        }

    val voiceSearchButton
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.voiceSearchButton
            OmnibarPosition.BOTTOM -> bottomOmnibar.voiceSearchButton
        }

    val spacer
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.spacer
            OmnibarPosition.BOTTOM -> bottomOmnibar.spacer
        }

    val trackersAnimation
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.trackersAnimation
            OmnibarPosition.BOTTOM -> bottomOmnibar.trackersAnimation
        }
}
