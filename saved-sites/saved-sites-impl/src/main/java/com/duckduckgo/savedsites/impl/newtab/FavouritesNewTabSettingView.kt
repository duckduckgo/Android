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
import android.view.View
import android.widget.LinearLayout
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.newtabpage.api.NewTabPageSection
import com.duckduckgo.newtabpage.api.NewTabPageSectionSettingsPlugin
import com.duckduckgo.saved.sites.impl.databinding.ViewFavouritesSettingsItemBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

class FavouritesNewTabSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    private val binding: ViewFavouritesSettingsItemBinding by viewBinding()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
class FavouritesNewTabSectionSettingsPlugin @Inject constructor() : NewTabPageSectionSettingsPlugin {
    override val name = NewTabPageSection.FAVOURITES.name

    override fun getView(context: Context): View {
        return FavouritesNewTabSettingView(context)
    }
}
