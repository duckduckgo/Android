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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.BottomAppBarBehavior
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.TopAppBarBehavior
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection

@InjectWith(FragmentScope::class)
class ScrollingOmnibarLayout @JvmOverloads constructor(
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

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> {
        return when (omnibarPosition) {
            OmnibarPosition.TOP -> TopAppBarBehavior(context, this)
            OmnibarPosition.BOTTOM -> BottomAppBarBehavior(context, this)
        }
    }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> super.setExpanded(expanded, animate)
            OmnibarPosition.BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }
}
