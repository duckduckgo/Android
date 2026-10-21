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

import com.duckduckgo.app.fire.fireproofwebsite.ui.AutomaticFireproofSetting
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealLegacyFireproofSettingsMigrationTest {

    private val settingsDataStore: SettingsDataStore = mock()
    private val testee = RealLegacyFireproofSettingsMigration(settingsDataStore)

    @Test
    fun whenLoginDetectionEnabledThenAutomaticFireproofSettingIsMapped() {
        whenever(settingsDataStore.appLoginDetection).thenReturn(true)

        testee.updateFireproofSettingType()

        verify(settingsDataStore).automaticFireproofSetting = AutomaticFireproofSetting.ASK_EVERY_TIME
    }

    @Test
    fun whenLoginDetectionDisabledThenAutomaticFireproofSettingIsMapped() {
        whenever(settingsDataStore.appLoginDetection).thenReturn(false)

        testee.updateFireproofSettingType()

        verify(settingsDataStore).automaticFireproofSetting = AutomaticFireproofSetting.NEVER
    }
}
