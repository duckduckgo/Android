/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.dev.settings

import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.dev.settings.db.UAOverride
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.user.agent.api.UserAgentInterceptor
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

@ContributesMultibinding(AppScope::class)
class InternalUAInterceptor @Inject constructor(
    private val devSettingsDataStore: DevSettingsDataStore,
    @Named("defaultUserAgent") private val defaultUserAgent: Provider<String>,
) : UserAgentInterceptor {

    override fun intercept(userAgent: String): String {
        if (!devSettingsDataStore.overrideUA) return userAgent

        return when (devSettingsDataStore.selectedUA) {
            UAOverride.DEFAULT -> userAgent
            UAOverride.FIREFOX -> "Mozilla/5.0 (Android 11; Mobile; rv:94.0) Gecko/94.0 Firefox/94.0"
            UAOverride.WEBVIEW -> defaultUserAgent.get()
        }
    }
}
