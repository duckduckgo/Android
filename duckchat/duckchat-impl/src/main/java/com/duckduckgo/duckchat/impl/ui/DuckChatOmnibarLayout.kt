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

package com.duckduckgo.duckchat.impl.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.ui.tabs.NewTabSwitcherButton
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.R
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection

@InjectWith(FragmentScope::class)
class DuckChatOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    internal val tabsMenu: NewTabSwitcherButton by lazy { findViewById(R.id.inputFieldTabsMenu) }
    internal val fireIconMenu: FrameLayout by lazy { findViewById(R.id.inputFieldFireButton) }
    internal val browserMenu: FrameLayout by lazy { findViewById(R.id.inputFieldBrowserMenu) }
    internal val historyMenu: FrameLayout by lazy { findViewById(R.id.duckAiHistoryButton) }
    internal val inputCard: View by lazy { findViewById(R.id.inputModeWidgetCard) }

    private var omnibarItemPressedListener: ItemPressedListener? = null

    interface ItemPressedListener {
        fun onTabsButtonPressed()

        fun onFireButtonPressed()

        fun onBrowserMenuPressed()

        fun onHistoryMenuPressed()
    }

    init {
        inflate(context, R.layout.view_duck_chat_omnibar, this)
        AndroidSupportInjection.inject(this)
    }

    fun setOmnibarItemPressedListener(itemPressedListener: ItemPressedListener) {
        omnibarItemPressedListener = itemPressedListener
        tabsMenu.setOnClickListener {
            omnibarItemPressedListener?.onTabsButtonPressed()
        }

        fireIconMenu.setOnClickListener {
            omnibarItemPressedListener?.onFireButtonPressed()
        }

        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }

        historyMenu.setOnClickListener {
            omnibarItemPressedListener?.onHistoryMenuPressed()
        }

        inputCard.setOnClickListener {
        }
    }

    fun setTabsCount(tabs: Int) {
        tabsMenu.count = tabs
    }
}
