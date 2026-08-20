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

package com.duckduckgo.app.settings

import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class SettingsDesktopBrowserPromotionHandlerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val settingsDataStoreMock: SettingsDataStore = mock()

    private val testee = SettingsDesktopBrowserPromotionHandler(
        settingsDataStore = settingsDataStoreMock,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenLinkCopiedThenSettingIsDismissed() = runTest {
        testee.onInteraction(Interaction.LINK_COPIED)

        verify(settingsDataStoreMock).getDesktopBrowserSettingDismissed = true
    }

    @Test
    fun whenShareCompletedThenSettingIsDismissed() = runTest {
        testee.onInteraction(Interaction.SHARE_COMPLETED)

        verify(settingsDataStoreMock).getDesktopBrowserSettingDismissed = true
    }

    @Test
    fun whenDismissedThenSettingIsDismissed() = runTest {
        testee.onInteraction(Interaction.DISMISSED)

        verify(settingsDataStoreMock).getDesktopBrowserSettingDismissed = true
    }
}
