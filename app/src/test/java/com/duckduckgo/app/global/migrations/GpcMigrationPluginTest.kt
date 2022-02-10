/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.migrations

import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.privacy.config.api.Gpc
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.Before
import org.junit.Test

class GpcMigrationPluginTest {

    private val mockSettingsDataStore: SettingsDataStore = mock()
    private val mockGpc: Gpc = mock()

    private lateinit var testee: GpcMigrationPlugin

    @Before
    fun before() {
        testee = GpcMigrationPlugin(mockSettingsDataStore, mockGpc)
    }

    @Test
    fun whenRunIfPreviousSettingWasEnabledThenEnableGpc() {
        whenever(mockSettingsDataStore.globalPrivacyControlEnabled).thenReturn(true)
        testee.run()
        verify(mockGpc).enableGpc()
    }

    @Test
    fun whenRunIfPreviousSettingWasDisabledThenDisableGpc() {
        whenever(mockSettingsDataStore.globalPrivacyControlEnabled).thenReturn(false)
        testee.run()
        verify(mockGpc).disableGpc()
    }
}
