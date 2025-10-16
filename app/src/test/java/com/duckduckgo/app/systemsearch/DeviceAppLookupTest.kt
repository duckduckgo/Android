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

package com.duckduckgo.app.systemsearch

import android.content.Intent
import com.duckduckgo.app.systemsearch.DeviceAppLookupTest.AppName.APP_WITH_RESERVED_CHARS
import com.duckduckgo.app.systemsearch.DeviceAppLookupTest.AppName.DDG_MOVIES
import com.duckduckgo.app.systemsearch.DeviceAppLookupTest.AppName.DDG_MUSIC
import com.duckduckgo.app.systemsearch.DeviceAppLookupTest.AppName.FILES
import com.duckduckgo.app.systemsearch.DeviceAppLookupTest.AppName.LIVE_DDG
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeviceAppLookupTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockAppProvider: DeviceAppListProvider = mock()

    private val testee = InstalledDeviceAppLookup(mockAppProvider, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenQueryMatchesWordInShortNameThenMatchesAreReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("DDG")
        assertEquals(3, result.size)
        assertEquals(DDG_MOVIES, result[0].shortName)
        assertEquals(DDG_MUSIC, result[1].shortName)
        assertEquals(LIVE_DDG, result[2].shortName)
    }

    @Test
    fun whenQueryMatchesWordPrefixInShortNameThenMatchesAreReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("DDG")
        assertEquals(3, result.size)
        assertEquals(DDG_MOVIES, result[0].shortName)
        assertEquals(DDG_MUSIC, result[1].shortName)
        assertEquals(LIVE_DDG, result[2].shortName)
    }

    @Test
    fun whenQueryMatchesPastShortNameWordBoundaryToNextPrefixThenMatchesAreReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("DDG M")
        assertEquals(2, result.size)
        assertEquals(DDG_MOVIES, result[0].shortName)
        assertEquals(DDG_MUSIC, result[1].shortName)
    }

    @Test
    fun whenQueryMatchesWordPrefixInShortNameWithDifferentCaseThenMatchesAreReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("ddg")
        assertEquals(3, result.size)
        assertEquals(DDG_MOVIES, result[0].shortName)
        assertEquals(DDG_MUSIC, result[1].shortName)
        assertEquals(LIVE_DDG, result[2].shortName)
    }

    @Test
    fun whenQueryMatchesMiddleOrSuffixOfAppNameWordThenNoAppsReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("DG")
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenQueryDoesNotMatchAnyPartOfAppNameThenNoAppsReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("nonmatching")
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenQueryIsEmptyThenNoAppsReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenAppsListIsEmptyThenNoAppsReturned() = runTest {
        whenever(mockAppProvider.get()).thenReturn(noApps)
        val result = testee.query("DDG")
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenQueryMatchesAppNameWithSpecialRegexCharactersThenAppReturnedWithoutCrashing() = runTest {
        whenever(mockAppProvider.get()).thenReturn(apps)
        val result = testee.query(APP_WITH_RESERVED_CHARS)
        assertEquals(1, result.size)
        assertEquals(APP_WITH_RESERVED_CHARS, result[0].shortName)
    }

    object AppName {
        const val DDG_MOVIES = "DDG Movies"
        const val DDG_MUSIC = "DDG Music"
        const val LIVE_DDG = "Live DDG"
        const val FILES = "Files"
        const val APP_WITH_RESERVED_CHARS = "APP.^\$*+-?()[]{}\\|"
    }

    companion object {
        val noApps = emptyList<DeviceApp>()

        val apps = listOf(
            DeviceApp(DDG_MOVIES, "", Intent()),
            DeviceApp(DDG_MUSIC, "", Intent()),
            DeviceApp(FILES, "", Intent()),
            DeviceApp(LIVE_DDG, "", Intent()),
            DeviceApp(APP_WITH_RESERVED_CHARS, "", Intent()),
        )
    }
}
