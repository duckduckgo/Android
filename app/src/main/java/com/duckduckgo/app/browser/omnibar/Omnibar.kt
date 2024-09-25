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
import android.content.res.TypedArray
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.mobile.android.R as CommonR

@SuppressLint("ClickableViewAccessibility")
class Omnibar(
    val omnibarPosition: OmnibarPosition,
    private val binding: FragmentBrowserTabBinding,
) {
    private val actionBarSize: Int by lazy {
        val array: TypedArray = binding.rootView.context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
        val actionBarSize = array.getDimensionPixelSize(0, -1)
        array.recycle()
        actionBarSize
    }

    val legacyOmnibar: LegacyOmnibarView by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.rootView.removeView(binding.legacyOmnibarBottom)
                binding.legacyOmnibar
            }
            OmnibarPosition.BOTTOM -> {
                binding.rootView.removeView(binding.legacyOmnibar)

                // remove the default top abb bar behavior
                removeAppBarBehavior(binding.autoCompleteSuggestionsList)
                removeAppBarBehavior(binding.browserLayout)
                removeAppBarBehavior(binding.focusedView)

                // add padding to the NTP to prevent the bottom toolbar from overlapping the settings button
                binding.includeNewBrowserTab.browserBackground.apply {
                    setPadding(paddingLeft, context.resources.getDimensionPixelSize(CommonR.dimen.keyline_2), paddingRight, actionBarSize)
                }

                // prevent the touch event leaking to the webView below
                binding.legacyOmnibarBottom.setOnTouchListener { _, _ -> true }

                binding.legacyOmnibarBottom
            }
        }
    }

    private fun removeAppBarBehavior(view: View) {
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    val findInPage = legacyOmnibar.findInPage
    val omnibarTextInput = legacyOmnibar.omnibarTextInput
    val tabsMenu = legacyOmnibar.tabsMenu
    val fireIconMenu = legacyOmnibar.fireIconMenu
    val browserMenu = legacyOmnibar.browserMenu
    val cookieDummyView = legacyOmnibar.cookieDummyView
    val cookieAnimation = legacyOmnibar.cookieAnimation
    val sceneRoot = legacyOmnibar.sceneRoot
    val omniBarContainer = legacyOmnibar.omniBarContainer
    val toolbar = legacyOmnibar.toolbar
    val toolbarContainer = legacyOmnibar.toolbarContainer
    val customTabToolbarContainer = legacyOmnibar.customTabToolbarContainer
    val browserMenuImageView = legacyOmnibar.browserMenuImageView
    val shieldIcon = legacyOmnibar.shieldIcon
    val pageLoadingIndicator = legacyOmnibar.pageLoadingIndicator
    val searchIcon = legacyOmnibar.searchIcon
    val daxIcon = legacyOmnibar.daxIcon
    val clearTextButton = legacyOmnibar.clearTextButton
    val fireIconImageView = legacyOmnibar.fireIconImageView
    val placeholder = legacyOmnibar.placeholder
    val voiceSearchButton = legacyOmnibar.voiceSearchButton
    val spacer = legacyOmnibar.spacer
    val trackersAnimation = legacyOmnibar.trackersAnimation
    val duckPlayerIcon = legacyOmnibar.duckPlayerIcon
}
