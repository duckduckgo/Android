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

package com.duckduckgo.app.browser.applinks

import com.duckduckgo.app.browser.applinks.AppLinksFeatureStateReporterPlugin.Companion.APP_LINKS_PARAM
import com.duckduckgo.app.settings.db.SettingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppLinksFeatureStateReporterPluginTest {

    private val settingsDataStore: SettingsDataStore = mock()
    private val testee = AppLinksFeatureStateReporterPlugin(settingsDataStore)

    @Test
    fun whenAppLinksEnabledAndPromptShownThenReturnsAskEverytime() {
        whenever(settingsDataStore.appLinksEnabled).thenReturn(true)
        whenever(settingsDataStore.showAppLinksPrompt).thenReturn(true)

        assertEquals("ask_everytime", testee.featureStateParams()[APP_LINKS_PARAM])
    }

    @Test
    fun whenAppLinksEnabledAndPromptNotShownThenReturnsAlways() {
        whenever(settingsDataStore.appLinksEnabled).thenReturn(true)
        whenever(settingsDataStore.showAppLinksPrompt).thenReturn(false)

        assertEquals("always", testee.featureStateParams()[APP_LINKS_PARAM])
    }

    @Test
    fun whenAppLinksDisabledThenReturnsNever() {
        whenever(settingsDataStore.appLinksEnabled).thenReturn(false)

        assertEquals("never", testee.featureStateParams()[APP_LINKS_PARAM])
    }
}
