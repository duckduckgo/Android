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

package com.duckduckgo.remote.messaging.impl.matchers

import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.ui.DuckDuckGoTheme
import com.duckduckgo.remote.messaging.impl.models.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class UserAttributeMatcherTest {

    private val userBrowserProperties: UserBrowserProperties = mock()

    private val testee = UserAttributeMatcher(userBrowserProperties)

    @Test
    fun whenAppThemeMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(appTheme = DuckDuckGoTheme.SYSTEM_DEFAULT)

        val result = testee.evaluate(
            AppTheme(value = "system_default"),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenAppThemeDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(appTheme = DuckDuckGoTheme.SYSTEM_DEFAULT)

        val result = testee.evaluate(
            AppTheme(value = "light"),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenBookmarksMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(value = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenBookmarksDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(value = 15),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenBookmarksEqualOrLowerThanMaxThenReturnMatch() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(max = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenBookmarksGreaterThanMaxThenReturnFail() = runTest {
        givenBrowserProperties(bookmarks = 15L)

        val result = testee.evaluate(
            Bookmarks(max = 10),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenBookmarksEqualOrGreaterThanMinThenReturnMatch() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(min = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenBookmarksLowerThanMinThenReturnFail() = runTest {
        givenBrowserProperties(bookmarks = 0L)

        val result = testee.evaluate(
            Bookmarks(min = 9),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenBookmarksInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(min = 9, max = 15),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenBookmarksNotInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            Bookmarks(min = 3, max = 6),
        )

        assertEquals(false, result)
    }

    // Favorites
    @Test
    fun whenFavoritesMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(value = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenFavoritesDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(value = 15),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenFavoritesEqualOrLowerThanMaxThenReturnMatch() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(max = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenFavoritesGreaterThanMaxThenReturnFail() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(max = 5),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenFavoritesEqualOrGreaterThanMinThenReturnMatch() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenFavoritesLowerThanMinThenReturnFail() = runTest {
        givenBrowserProperties(favorites = 0L)

        val result = testee.evaluate(
            Favorites(min = 10),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenFavoritesInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 9, max = 15),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenFavoritesNotInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 3, max = 6),
        )

        assertEquals(false, result)
    }

    // DaysSinceInstalled
    @Test
    fun whenDaysSinceInstalledEqualOrLowerThanMaxThenReturnMatch() = runTest {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            DaysSinceInstalled(max = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDaysSinceInstalledGreaterThanMaxThenReturnFail() = runTest {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            DaysSinceInstalled(max = 5),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenDaysSinceInstalledEqualOrGreaterThanMinThenReturnMatch() = runTest {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            DaysSinceInstalled(min = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDaysSinceInstalledLowerThanMinThenReturnFail() = runTest {
        givenBrowserProperties(daysSinceInstalled = 1L)

        val result = testee.evaluate(
            DaysSinceInstalled(min = 10),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenDaysSinceInstalledInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            DaysSinceInstalled(min = 9, max = 15),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDaysSinceInstalledNotInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            DaysSinceInstalled(min = 3, max = 6),
        )

        assertEquals(false, result)
    }

    // DaysUsedSince
    @Test
    fun whenDaysUsedSinceMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(daysUsedSince = 10L)

        val result = testee.evaluate(
            DaysUsedSince(since = Date(), value = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDaysUsedSinceDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(daysUsedSince = 10L)

        val result = testee.evaluate(
            DaysUsedSince(since = Date(), value = 8),
        )

        assertEquals(false, result)
    }

    // DefaultBrowser
    @Test
    fun whenDefaultBrowserMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(defaultBrowser = true)

        val result = testee.evaluate(
            DefaultBrowser(value = true),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDefaultBrowserDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(defaultBrowser = false)

        val result = testee.evaluate(
            DefaultBrowser(value = true),
        )

        assertEquals(false, result)
    }

    // EmailEnabled
    @Test
    fun whenEmailEnabledMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(emailEnabled = true)

        val result = testee.evaluate(
            EmailEnabled(value = true),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenEmailEnabledDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(emailEnabled = false)

        val result = testee.evaluate(
            EmailEnabled(value = true),
        )

        assertEquals(false, result)
    }

    // SearchCount
    @Test
    fun whenSearchCountMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(value = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenSearchCountDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(value = 15),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenSearchCountEqualOrLowerThanMaxThenReturnMatch() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(max = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenSearchCountGreaterThanMaxThenReturnFail() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(max = 5),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenSearchCountEqualOrGreaterThanMinThenReturnMatch() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(min = 10),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenSearchCountLowerThanMinThenReturnFail() = runTest {
        givenBrowserProperties(searchCount = 1L)

        val result = testee.evaluate(
            SearchCount(min = 10),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenSearchCountInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(min = 10, max = 15),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenSearchCountNotInRangeThenReturnMatch() = runTest {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            SearchCount(min = 3, max = 6),
        )

        assertEquals(false, result)
    }

    // WidgetAdded
    @Test
    fun whenWidgetAddedMatchesThenReturnMatch() = runTest {
        givenBrowserProperties(widgetAdded = true)

        val result = testee.evaluate(
            WidgetAdded(value = true),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenWidgetAddedDoesNotMatchThenReturnFail() = runTest {
        givenBrowserProperties(widgetAdded = false)

        val result = testee.evaluate(
            WidgetAdded(value = true),
        )

        assertEquals(false, result)
    }

    private suspend fun givenBrowserProperties(
        appTheme: DuckDuckGoTheme = DuckDuckGoTheme.SYSTEM_DEFAULT,
        bookmarks: Long = 8L,
        favorites: Long = 8L,
        daysSinceInstalled: Long = 8L,
        daysUsedSince: Long = 8L,
        defaultBrowser: Boolean = true,
        emailEnabled: Boolean = true,
        searchCount: Long = 8L,
        widgetAdded: Boolean = true,
    ) {
        whenever(userBrowserProperties.appTheme()).thenReturn(appTheme)
        whenever(userBrowserProperties.bookmarks()).thenReturn(bookmarks)
        whenever(userBrowserProperties.daysSinceInstalled()).thenReturn(daysSinceInstalled)
        whenever(userBrowserProperties.daysUsedSince(any())).thenReturn(daysUsedSince)
        whenever(userBrowserProperties.defaultBrowser()).thenReturn(defaultBrowser)
        whenever(userBrowserProperties.emailEnabled()).thenReturn(emailEnabled)
        whenever(userBrowserProperties.favorites()).thenReturn(favorites)
        whenever(userBrowserProperties.searchCount()).thenReturn(searchCount)
        whenever(userBrowserProperties.widgetAdded()).thenReturn(widgetAdded)
    }
}
