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
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.NewSettingsFeature
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingView
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingView
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingView
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingView
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(100)
class ProSettingsTitle @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return SectionHeaderListItem(context).apply {
            primaryText = context.getString(R.string.privacyPro)
        }
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(500)
class ProSettings @Inject constructor(private val newSettingsFeature: NewSettingsFeature) : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return if(newSettingsFeature.self().isEnabled()) {
            ProSettingView(context)
        } else {
            LegacyProSettingView(context)
        }
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(300)
class PIRSettings @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return PirSettingView(context)
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(400)
class ITRSettings @Inject constructor() : ProSettingsPlugin {
    override fun getView(context: Context): View {
        return ItrSettingView(context)
    }
}
