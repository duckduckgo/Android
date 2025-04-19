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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrowserAdditionalPixelParamPluginTest {
    @Test
    fun whenOnboardingIsNotActiveThenPluginShouldReturnParamTrue() = runTest {
        val userStageStore: UserStageStore = mock()
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        val plugin = AppOnboardingCompletedAdditionalPixelParamPlugin(userStageStore)

        assertEquals("appOnboardingCompleted" to "true", plugin.params())
    }

    @Test
    fun whenOnboardingIsActiveThenPluginShouldReturnParamFalse() = runTest {
        val userStageStore: UserStageStore = mock()
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        val plugin = AppOnboardingCompletedAdditionalPixelParamPlugin(userStageStore)

        assertEquals("appOnboardingCompleted" to "false", plugin.params())
    }

    @Test
    fun whenEmailIsEnabledThenPluginShouldReturnParamTrue() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.emailEnabled()).thenReturn(true)
        val plugin = EmailEnabledAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("emailEnabled" to "true", plugin.params())
    }

    @Test
    fun whenEmailIsNotEnabledThenPluginShouldReturnParamFalse() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.emailEnabled()).thenReturn(false)
        val plugin = EmailEnabledAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("emailEnabled" to "false", plugin.params())
    }

    @Test
    fun whenWidgetAddedThenPluginShouldReturnParamTrue() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.widgetAdded()).thenReturn(true)
        val plugin = WidgetAddedAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("widgetAdded" to "true", plugin.params())
    }

    @Test
    fun whenNoFireproofedWebsitesThenPluginShouldReturnParamFalse() = runTest {
        val fireproofRepository: FireproofRepository = mock()
        whenever(fireproofRepository.fireproofWebsites()).thenReturn(emptyList())
        val plugin = FireProofingUsedAdditionalPixelParamPlugin(fireproofRepository)

        assertEquals("fireproofingUsed" to "false", plugin.params())
    }

    @Test
    fun whenWithFireproofedWebsitesThenPluginShouldReturnParamTrue() = runTest {
        val fireproofRepository: FireproofRepository = mock()
        whenever(fireproofRepository.fireproofWebsites()).thenReturn(listOf("hello.com", "test.com"))
        val plugin = FireProofingUsedAdditionalPixelParamPlugin(fireproofRepository)

        assertEquals("fireproofingUsed" to "true", plugin.params())
    }

    @Test
    fun whenDaysSinceLastUsedIsLessThan2ThenPluginShouldReturnParamTrue() = runTest {
        val appDaysUsedRepository: AppDaysUsedRepository = mock()
        val currentTimeProvider: CurrentTimeProvider = mock()
        whenever(appDaysUsedRepository.getLastActiveDay()).thenReturn("2024-07-17")
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1721214994912L)
        val plugin = FrequentUserAdditionalPixelParamPlugin(appDaysUsedRepository, currentTimeProvider)

        assertEquals("frequentUser" to "true", plugin.params())
    }

    @Test
    fun whenDaysSinceLastUsedIs2ThenPluginShouldReturnParamFalse() = runTest {
        val appDaysUsedRepository: AppDaysUsedRepository = mock()
        val currentTimeProvider: CurrentTimeProvider = mock()
        whenever(appDaysUsedRepository.getLastActiveDay()).thenReturn("2024-07-15")
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1721214994912L)
        val plugin = FrequentUserAdditionalPixelParamPlugin(appDaysUsedRepository, currentTimeProvider)

        assertEquals("frequentUser" to "false", plugin.params())
    }

    @Test
    fun whenDaysSinceLastUsedIsMoreThan2ThenPluginShouldReturnParamFalse() = runTest {
        val appDaysUsedRepository: AppDaysUsedRepository = mock()
        val currentTimeProvider: CurrentTimeProvider = mock()
        whenever(appDaysUsedRepository.getLastActiveDay()).thenReturn("2023-07-17")
        whenever(currentTimeProvider.currentTimeMillis()).thenReturn(1721214994912L)
        val plugin = FrequentUserAdditionalPixelParamPlugin(appDaysUsedRepository, currentTimeProvider)

        assertEquals("frequentUser" to "false", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIsLessThan30ThenPluginShouldReturnParamFalse() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(1)
        val plugin = LongTermUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("longTermUser" to "false", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIs30ThenPluginShouldReturnParamFalse() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(30)
        val plugin = LongTermUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("longTermUser" to "false", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIsGreaterThan30ThenPluginShouldReturnParamTrue() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(31)
        val plugin = LongTermUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("longTermUser" to "true", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIsGreaterThan50ThenPluginShouldReturnParamTrue() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.searchCount()).thenReturn(51)
        val plugin = SearchUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("searchUser" to "true", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIs50ThenPluginShouldReturnParamFalse() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.searchCount()).thenReturn(50)
        val plugin = SearchUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("searchUser" to "false", plugin.params())
    }

    @Test
    fun whenDaysSinceInstalledIsLessThan50ThenPluginShouldReturnParamFalse() = runTest {
        val userBrowserProperties: UserBrowserProperties = mock()
        whenever(userBrowserProperties.searchCount()).thenReturn(10)
        val plugin = SearchUserAdditionalPixelParamPlugin(userBrowserProperties)

        assertEquals("searchUser" to "false", plugin.params())
    }

    @Test
    fun whenTabCountIsGreaterThan3ThenPluginShouldReturnParamTrue() = runTest {
        val tabsDao: TabsDao = mock()
        whenever(tabsDao.tabs()).thenReturn(
            listOf(
                TabEntity(tabId = "tabdid1", position = 1),
                TabEntity(tabId = "tabdid2", position = 2),
                TabEntity(tabId = "tabdid3", position = 3),
                TabEntity(tabId = "tabdid4", position = 4),
            ),
        )
        val plugin = ValidOpenTabsCountAdditionalPixelParamPlugin(tabsDao)

        assertEquals("validOpenTabsCount" to "true", plugin.params())
    }

    @Test
    fun whenNoTabsThenPluginShouldReturnParamFalse() = runTest {
        val tabsDao: TabsDao = mock()
        whenever(tabsDao.tabs()).thenReturn(emptyList())
        val plugin = ValidOpenTabsCountAdditionalPixelParamPlugin(tabsDao)

        assertEquals("validOpenTabsCount" to "false", plugin.params())
    }

    @Test
    fun whenTabCountIs3ThenPluginShouldReturnParamFalse() = runTest {
        val tabsDao: TabsDao = mock()
        whenever(tabsDao.tabs()).thenReturn(
            listOf(
                TabEntity(tabId = "tabdid1", position = 1),
                TabEntity(tabId = "tabdid2", position = 2),
                TabEntity(tabId = "tabdid3", position = 3),
            ),
        )
        val plugin = ValidOpenTabsCountAdditionalPixelParamPlugin(tabsDao)

        assertEquals("validOpenTabsCount" to "false", plugin.params())
    }

    @Test
    fun whenFirebuttonUseCountIsGreaterThan5ThenPluginShouldReturnParamTrue() = runTest {
        val fireButtonStore: FireButtonStore = mock()
        whenever(fireButtonStore.fireButttonUseCount).thenReturn(10)
        val plugin = FireButtonUsedAdditionalPixelParamPlugin(fireButtonStore)

        assertEquals("fireButtonUsed" to "true", plugin.params())
    }

    @Test
    fun whenFirebuttonUseCountIs5ThenPluginShouldReturnParamFalse() = runTest {
        val fireButtonStore: FireButtonStore = mock()
        whenever(fireButtonStore.fireButttonUseCount).thenReturn(5)
        val plugin = FireButtonUsedAdditionalPixelParamPlugin(fireButtonStore)

        assertEquals("fireButtonUsed" to "false", plugin.params())
    }

    @Test
    fun whenFirebuttonUseCountIsLessThan5ThenPluginShouldReturnParamFalse() = runTest {
        val fireButtonStore: FireButtonStore = mock()
        whenever(fireButtonStore.fireButttonUseCount).thenReturn(1)
        val plugin = FireButtonUsedAdditionalPixelParamPlugin(fireButtonStore)

        assertEquals("fireButtonUsed" to "false", plugin.params())
    }
}
