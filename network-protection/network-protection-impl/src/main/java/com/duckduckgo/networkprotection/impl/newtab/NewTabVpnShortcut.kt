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

package com.duckduckgo.networkprotection.impl.newtab

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import com.duckduckgo.newtabpage.api.NewTabShortcut.Vpn
import javax.inject.Inject

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
    priority = 6,
)
class VpnNewTabShortcutPlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val setting: VpnNewTabShortcutSetting,
) : NewTabPageShortcutPlugin {
    override fun getShortcut(): NewTabShortcut {
        return Vpn
    }

    override fun onClick(context: Context) {
        globalActivityStarter.start(context, NetworkProtectionManagementScreenNoParams)
    }

    override suspend fun isUserEnabled(): Boolean {
        return setting.self().isEnabled()
    }

    override suspend fun toggle() {
        if (setting.self().isEnabled()) {
            setting.self().setEnabled(Toggle.State(false))
        } else {
            setting.self().setEnabled(Toggle.State(true))
        }
    }
}

/**
 * Local feature/settings - they will never be in remote config
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "vpnNewTabShortcutSetting",
)
interface VpnNewTabShortcutSetting {
    @Toggle.DefaultValue(false)
    fun self(): Toggle
}
