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

package com.duckduckgo.savedsites.impl.newtab

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.browser.api.ui.BrowserScreens.BookmarksScreenNoParams
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.mobile.android.databinding.RowNewTabGridItemBinding
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.api.NewTabShortcut.Bookmarks
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(ViewScope::class)
class BookmarksShortcutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: RowNewTabGridItemBinding by viewBinding()

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.root.setOnClickListener {
            globalActivityStarter.start(this.context, BookmarksScreenNoParams)
        }
    }
}

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
)
class BookmarksNewTabShortcutPlugin @Inject constructor() : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return Bookmarks
    }
}
