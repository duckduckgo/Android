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

package com.duckduckgo.app.browser.cookies.thirdpartycookienames.store

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ThirdPartyCookieNamesSettingsRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDao: ThirdPartyCookieNamesDao = mock()

    lateinit var repository: ThirdPartyCookieNamesSettingsRepository

    @Test
    fun whenRepositoryIsCreatedThenCookieNamesLoadedIntoMemory() {
        givenDaoContainsCookieNames()

        repository = RealThirdPartyCookieNamesSettingsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDao, isMainProcess = true)

        assertEquals(COOKIE_NAME, repository.cookieNames.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() = runTest {
        repository = RealThirdPartyCookieNamesSettingsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDao, isMainProcess = true)

        repository.updateAllSettings(ThirdPartyCookieNamesSettings(listOf()))

        verify(mockDao).updateAllCookieNames(anyList())
    }

    @Test
    fun whenUpdateAllThenPreviousCookieNamesAreCleared() = runTest {
        givenDaoContainsCookieNames()
        repository = RealThirdPartyCookieNamesSettingsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDao, isMainProcess = true)

        assertEquals(1, repository.cookieNames.size)

        reset(mockDao)

        repository.updateAllSettings(ThirdPartyCookieNamesSettings(listOf()))

        assertEquals(0, repository.cookieNames.size)
    }

    private fun givenDaoContainsCookieNames() {
        whenever(mockDao.getCookieNames()).thenReturn(listOf(cookieNamesEntity))
    }

    companion object {
        private const val COOKIE_NAME = "cookieName"
        val cookieNamesEntity = CookieNamesEntity(COOKIE_NAME)
    }
}
