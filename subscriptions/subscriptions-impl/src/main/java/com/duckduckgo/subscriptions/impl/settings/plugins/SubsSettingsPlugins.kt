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
import android.widget.LinearLayout
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingsHeaderNodeId.PPro
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.common.ui.view.divider.HorizontalDivider
import com.duckduckgo.common.ui.view.listitem.SectionHeaderListItem
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.subscriptions.impl.R
import com.duckduckgo.subscriptions.impl.settings.views.ItrSettingView
import com.duckduckgo.subscriptions.impl.settings.views.LegacyItrSettingView
import com.duckduckgo.subscriptions.impl.settings.views.LegacyPirSettingView
import com.duckduckgo.subscriptions.impl.settings.views.LegacyProSettingView
import com.duckduckgo.subscriptions.impl.settings.views.PirSettingView
import com.duckduckgo.subscriptions.impl.settings.views.ProSettingView
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(204)
class PrivacyProSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = PPro
    override val children: List<SettingsNode> = emptyList()
    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return ProSettingView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "subscription", "privacy", "pro", "privacy pro",
            "vpn", "protection", "tunnel", "tunneling", "ip", "ip address",
            "pir", "information", "removal", "private",
            "itr", "theft", "identity", "restoration",
        )
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(202)
class PIRSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = PPro
    override val children: List<SettingsNode> = emptyList()
    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return PirSettingView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "privacy", "pro", "privacy pro", "pir", "information", "removal", "private",
        )
    }
}

@ContributesMultibinding(scope = ActivityScope::class)
@PriorityKey(203)
class ITRSettingNode @Inject constructor() : RootSettingsNode {
    override val settingsHeaderNodeId = PPro
    override val children: List<SettingsNode> = emptyList()
    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return ItrSettingView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "privacy", "pro", "privacy pro", "itr", "theft", "identity", "restoration",
        )
    }
}
