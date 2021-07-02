/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser.autocomplete

import android.content.Context
import com.duckduckgo.app.dev.db.DevSettingsDataStore
import com.duckduckgo.app.dev.db.DevSettingsSharedPreferences
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.settings.db.SettingsSharedPreferences
import com.duckduckgo.app.statistics.VariantManager
import dagger.Module
import dagger.Provides

@Module
class BrowserAutoCompleteModule {

    @Provides
    fun settingsDataStore(context: Context, variantManager: VariantManager): SettingsDataStore = SettingsSharedPreferences(context, variantManager)

    @Provides
    fun devSettingsDataStore(context: Context): DevSettingsDataStore = DevSettingsSharedPreferences(context)
}
