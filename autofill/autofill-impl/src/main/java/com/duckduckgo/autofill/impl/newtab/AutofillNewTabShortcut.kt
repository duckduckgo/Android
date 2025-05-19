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

package com.duckduckgo.autofill.impl.newtab

import android.content.Context
import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.autofill.api.AutofillScreenLaunchSource
import com.duckduckgo.autofill.api.AutofillScreens.AutofillPasswordsManagementScreen
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.newtabpage.api.NewTabPageShortcutPlugin
import com.duckduckgo.newtabpage.api.NewTabShortcut
import javax.inject.Inject

@ContributesActivePlugin(
    AppScope::class,
    boundType = NewTabPageShortcutPlugin::class,
    priority = NewTabPageShortcutPlugin.PRIORITY_AUTOFILL,
)
class AutofillNewTabShortcutPlugin @Inject constructor(
    private val globalActivityStarter: GlobalActivityStarter,
    private val setting: AutofillNewTabShortcutSetting,
) : NewTabPageShortcutPlugin {

    inner class PasswordsShortcut() : NewTabShortcut {
        override fun name(): String = "passwords"
        override fun titleResource(): Int = R.string.newTabPageShortcutPasswords
        override fun iconResource(): Int = R.drawable.ic_shortcut_passwords
    }

    override fun getShortcut(): NewTabShortcut {
        return PasswordsShortcut()
    }

    override fun onClick(context: Context) {
        globalActivityStarter.start(context, AutofillPasswordsManagementScreen(AutofillScreenLaunchSource.NewTabShortcut))
    }

    override suspend fun isUserEnabled(): Boolean {
        return setting.self().isEnabled()
    }

    override suspend fun setUserEnabled(enabled: Boolean) {
        if (enabled) {
            setting.self().setRawStoredState(Toggle.State(true))
        } else {
            setting.self().setRawStoredState(Toggle.State(false))
        }
    }
}

/**
 * Local feature/settings - they will never be in remote config
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "autofillNewTabShortcutSetting",
)
interface AutofillNewTabShortcutSetting {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
