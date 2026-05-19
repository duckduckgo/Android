/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.launch.seeder

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.testseeder.api.OmnibarPositionWriter
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealOmnibarPositionWriter @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : OmnibarPositionWriter {

    override fun setFromKey(positionKey: String) {
        when (positionKey.lowercase()) {
            "top" -> settingsDataStore.omnibarType = OmnibarType.SINGLE_TOP
            "bottom" -> settingsDataStore.omnibarType = OmnibarType.SINGLE_BOTTOM
            "split" -> settingsDataStore.omnibarType = OmnibarType.SPLIT
        }
    }
}
