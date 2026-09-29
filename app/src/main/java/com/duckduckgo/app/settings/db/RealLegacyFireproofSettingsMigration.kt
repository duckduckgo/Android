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

package com.duckduckgo.app.settings.db

import com.duckduckgo.app.global.db.LegacyFireproofSettingsMigration
import com.duckduckgo.app.settings.db.SettingsSharedPreferences.LoginDetectorPrefsMapper
import javax.inject.Inject

class RealLegacyFireproofSettingsMigration @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : LegacyFireproofSettingsMigration {

    private val loginDetectorPrefsMapper = LoginDetectorPrefsMapper()

    override fun updateFireproofSettingType() {
        settingsDataStore.automaticFireproofSetting =
            loginDetectorPrefsMapper.mapToAutomaticFireproofSetting(settingsDataStore.appLoginDetection)
    }
}
