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
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.databinding.ViewOmnibarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Action
import com.duckduckgo.app.browser.omnibar.Omnibar.Content
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.ViewViewModelFactory
import com.duckduckgo.di.scopes.ViewScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import javax.inject.Inject

interface Omnibar {

    // setActionListener?
    fun onAction(actionHandler: (Action) -> Unit)

    // setContentListener?
    fun onContent(contentHandler: (Content) -> Unit)
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
}

@InjectWith(ViewScope::class)
class OmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), Omnibar {

    @Inject
    lateinit var viewModelFactory: ViewViewModelFactory

    private val binding: ViewOmnibarBinding by viewBinding()

    private val viewModel: OmnibarViewModel by lazy {
        ViewModelProvider(findViewTreeViewModelStoreOwner()!!, viewModelFactory)[OmnibarViewModel::class.java]
    }

    override fun onAction(actionHandler: (Action) -> Unit) {
    }

    override fun onContent(contentHandler: (Content) -> Unit) {
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
}
