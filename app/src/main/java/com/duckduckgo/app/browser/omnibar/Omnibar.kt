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

import android.content.res.TypedArray
import android.view.View
import android.webkit.WebView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.duckduckgo.app.browser.BrowserWebViewClient
import com.duckduckgo.app.browser.DuckDuckGoWebView
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeOmnibarToolbarBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

class Omnibar(
    val omnibarPosition: OmnibarPosition,
    private val binding: FragmentBrowserTabBinding,
    private val coroutineScope: CoroutineScope
) {
    private val topOmnibar = IncludeOmnibarToolbarBinding.bind(binding.rootView)
    private val bottomOmnibar = binding.bottomToolbarInclude

    private val actionBarSize: Int by lazy {
        val array: TypedArray = binding.rootView.context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
        val actionBarSize = array.getDimensionPixelSize(0, -1)
        array.recycle()
        actionBarSize
    }

    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.rootView.removeView(bottomOmnibar.appBarLayout)
            }

            OmnibarPosition.BOTTOM -> {
                binding.rootView.removeView(topOmnibar.appBarLayout)

                removeAppBarBehavior(binding.autoCompleteSuggestionsList)
                removeAppBarBehavior(binding.browserLayout)
                removeAppBarBehavior(binding.focusedViewContainerLayout)
                binding.includeNewBrowserTab.browserBackground.apply {
                    setPadding(paddingLeft, context.resources.getDimensionPixelSize(CommonR.dimen.keyline_2), paddingRight, actionBarSize)
                }
            }
        }
    }

    private fun removeAppBarBehavior(view: View) {
        val layoutParams = view.layoutParams as CoordinatorLayout.LayoutParams
        layoutParams.behavior = null
        view.layoutParams = layoutParams
    }

    fun registerOnPageFinishedListener(browserChromeClient: BrowserWebViewClient) {
        if (omnibarPosition == OmnibarPosition.BOTTOM) {
            browserChromeClient.onPageFinishedListener = ::onPageFinished
        }
    }

    /**
     * This method prevents the toolbar from overlapping the content of the page when the page is not scrollable.
     *
     * It checks if the page is scrollable and if it is, it makes the bottom toolbar
     * collapsible. If the page is not scrollable, it makes the bottom toolbar always visible and adjusts the bottom padding of the WebView by the
     * toolbar height.
     *
     */
    private fun onPageFinished(webView: WebView) {
        (webView as? DuckDuckGoWebView)?.let { duckDuckGoWebView ->
            coroutineScope.launch {
                val viewPortHeight = duckDuckGoWebView.getWebContentHeight()
                if (viewPortHeight != 0) {
                    val screenHeight = binding.rootView.height
                    val appBarLayout = binding.bottomToolbarInclude.appBarLayout
                    if (viewPortHeight <= screenHeight) {
                        // make the bottom toolbar fixed and adjust the padding of the WebView
                        appBarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                            if (behavior != null) {
                                (behavior as? BottomAppBarBehavior)?.apply {
                                    animateToolbarVisibility(appBarLayout, true)
                                }
                                behavior = null
                            }
                        }

                        binding.webViewContainer.updatePadding(
                            bottom = appBarLayout.height,
                        )
                    } else {
                        // make the bottom toolbar collapsible
                        binding.webViewContainer.updatePadding(bottom = 0)
                        appBarLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                            behavior = BottomAppBarBehavior<View>(binding.rootView.context, null)
                        }
                    }
                }
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

    val duckPlayerIcon
        get() = when (omnibarPosition) {
            OmnibarPosition.TOP -> topOmnibar.duckPlayerIcon
            OmnibarPosition.BOTTOM -> bottomOmnibar.duckPlayerIcon
        }
}
