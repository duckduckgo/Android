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

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.app.browser.databinding.ViewLegacyOmnibarBinding
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.google.android.material.appbar.AppBarLayout

class LegacyOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    private val binding: ViewLegacyOmnibarBinding by viewBinding()

    val findInPage
        get() = binding.findInPage

    val omnibarTextInput
        get() = binding.omnibarTextInput

    val tabsMenu
        get() = binding.tabsMenu

    val fireIconMenu
        get() = binding.fireIconMenu

    val browserMenu
        get() = binding.browserMenu

    val cookieDummyView
        get() = binding.cookieDummyView

    val cookieAnimation
        get() = binding.cookieAnimation

    val sceneRoot
        get() = binding.sceneRoot

    val omniBarContainer
        get() = binding.omniBarContainer

    val toolbar
        get() = binding.toolbar

    val toolbarContainer
        get() = binding.toolbarContainer

    val customTabToolbarContainer
        get() = binding.customTabToolbarContainer

    val browserMenuImageView
        get() = binding.browserMenuImageView

    val shieldIcon
        get() = binding.shieldIcon

    val pageLoadingIndicator
        get() = binding.pageLoadingIndicator

    val searchIcon
        get() = binding.searchIcon

    val daxIcon
        get() = binding.daxIcon

    val clearTextButton
        get() = binding.clearTextButton

    val fireIconImageView
        get() = binding.fireIconImageView

    val placeholder
        get() = binding.placeholder

    val voiceSearchButton
        get() = binding.voiceSearchButton

    val spacer
        get() = binding.spacer

    val trackersAnimation
        get() = binding.trackersAnimation

    val duckPlayerIcon
        get() = binding.duckPlayerIcon
}
