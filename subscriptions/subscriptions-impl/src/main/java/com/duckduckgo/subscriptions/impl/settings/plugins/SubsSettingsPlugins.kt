/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.settings.plugins

import android.content.Context
import android.view.View
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingBuyView
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingView
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.MapKey
import javax.inject.Inject

@MapKey
annotation class PositionKey(val pos: Int)

@ContributesMultibinding(ActivityScope::class)
@PositionKey(100)
class ProSettingsTitle @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return SectionHeaderListItem(context).apply {
            primaryText = context.getString(R.string.privacyPro)
        }
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PositionKey(200)
class ProSettingBuy @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return ProSettingBuyView(context)
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PositionKey(300)
class ProSettings @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return ProSettingView(context)
    }
}
