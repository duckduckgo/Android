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

package com.duckduckgo.networkprotection.impl.subscription.settings

import android.content.Context
import android.view.View
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.common.ui.settings.RootSettingsNode
import com.duckduckgo.common.ui.settings.SettingsNode
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.settings.api.ProSettingsPlugin
import com.duckduckgo.settings.api.SettingsPageFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.UUID
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
@PriorityKey(201)
class ProSettingsNetP @Inject constructor() : RootSettingsNode {

    override val categoryNameResId = R.string.privacyPro
    override val children: List<SettingsNode> = emptyList()

    override val id: UUID = UUID.randomUUID()

    override fun getView(context: Context): View {
        return ProSettingNetPView(context, searchableId = id)
    }

    override fun generateKeywords(): Set<String> {
        return setOf(
            "vpn", "protection", "tunnel", "tunneling", "ip", "ip address",
        )
    }
}
