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
import com.duckduckgo.settings.api.SubsSettingsPlugin
import com.duckduckgo.subscriptions.impl.settings.views.SubsSettingBuyView
import com.duckduckgo.subscriptions.impl.settings.views.SubsSettingsTitleView
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.MapKey
import javax.inject.Inject

@MapKey
annotation class PositionKey(val pos: Int)

@ContributesMultibinding(ActivityScope::class)
@PositionKey(100)
class SubsSettingsTitle @Inject constructor() : SubsSettingsPlugin {
    override fun getView(context: Context): View {
        return SubsSettingsTitleView(context)
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PositionKey(101)
class SubsSettingBuy @Inject constructor() : SubsSettingsPlugin {
    override fun getView(context: Context): View {
        return SubsSettingBuyView(context)
    }
}
