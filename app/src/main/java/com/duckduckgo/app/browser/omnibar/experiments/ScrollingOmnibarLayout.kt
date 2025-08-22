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

package com.duckduckgo.app.browser.omnibar.experiments

import android.content.Context
import android.util.AttributeSet
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection

@InjectWith(FragmentScope::class)
class ScrollingOmnibarLayout @JvmOverloads constructor( // todo lp - remove the class
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    init {
        val attr =
            context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        omnibarPosition =
            OmnibarPosition.entries[attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_new_omnibar_bottom
        } else {
            R.layout.view_new_omnibar
        }
        inflate(context, layout, this)

        AndroidSupportInjection.inject(this)
    }
}
