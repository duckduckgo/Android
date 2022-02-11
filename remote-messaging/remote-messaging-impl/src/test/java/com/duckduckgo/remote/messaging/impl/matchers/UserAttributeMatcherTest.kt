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
import com.duckduckgo.mobile.android.ui.DuckDuckGoTheme
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Favorites
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class UserAttributeMatcherTest {

    private val userBrowserProperties: UserBrowserProperties = mock()

    private val testee = UserAttributeMatcher(userBrowserProperties)

    @Test
    fun whenAppThemeMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(appTheme = DuckDuckGoTheme.SYSTEM_DEFAULT)

        val result = testee.evaluate(
            MatchingAttribute.AppTheme(value = "system_default")
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenAppThemeDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(appTheme = DuckDuckGoTheme.SYSTEM_DEFAULT)

        val result = testee.evaluate(
            MatchingAttribute.AppTheme(value = "light")
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenBookmarksMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(value = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenBookmarksDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(value = 15)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenBookmarksEqualOrLowerThanMaxThenReturnMatch() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(max = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenBookmarksGreaterThanMaxThenReturnFail() = runBlocking {
        givenBrowserProperties(bookmarks = 15L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(max = 10)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenBookmarksEqualOrGreaterThanMinThenReturnMatch() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(min = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenBookmarksLowerThanMinThenReturnFail() = runBlocking {
        givenBrowserProperties(bookmarks = 0L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(min = 9)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenBookmarksInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(min = 9, max = 15)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenBookmarksNotInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(bookmarks = 10L)

        val result = testee.evaluate(
            MatchingAttribute.Bookmarks(min = 3, max = 6)
        )

        assertEquals(Result.Fail, result)
    }

    // Favorites
    @Test
    fun whenFavoritesMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(value = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenFavoritesDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(value = 15)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenFavoritesEqualOrLowerThanMaxThenReturnMatch() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(max = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenFavoritesGreaterThanMaxThenReturnFail() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(max = 5)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenFavoritesEqualOrGreaterThanMinThenReturnMatch() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenFavoritesLowerThanMinThenReturnFail() = runBlocking {
        givenBrowserProperties(favorites = 0L)

        val result = testee.evaluate(
            Favorites(min = 10)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenFavoritesInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 9, max = 15)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenFavoritesNotInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(favorites = 10L)

        val result = testee.evaluate(
            Favorites(min = 3, max = 6)
        )

        assertEquals(Result.Fail, result)
    }

    // DaysSinceInstalled
    @Test
    fun whenDaysSinceInstalledEqualOrLowerThanMaxThenReturnMatch() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(max = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenDaysSinceInstalledGreaterThanMaxThenReturnFail() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(max = 5)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenDaysSinceInstalledEqualOrGreaterThanMinThenReturnMatch() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(min = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenDaysSinceInstalledLowerThanMinThenReturnFail() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 1L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(min = 10)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenDaysSinceInstalledInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(min = 9, max = 15)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenDaysSinceInstalledNotInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(daysSinceInstalled = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysSinceInstalled(min = 3, max = 6)
        )

        assertEquals(Result.Fail, result)
    }

    // DaysUsedSince
    @Test
    fun whenDaysUsedSinceMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(daysUsedSince = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysUsedSince(since = Date(), value = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenDaysUsedSinceDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(daysUsedSince = 10L)

        val result = testee.evaluate(
            MatchingAttribute.DaysUsedSince(since = Date(), value = 8)
        )

        assertEquals(Result.Fail, result)
    }

    // DefaultBrowser
    @Test
    fun whenDefaultBrowserMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(defaultBrowser = true)

        val result = testee.evaluate(
            MatchingAttribute.DefaultBrowser(value = true)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenDefaultBrowserDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(defaultBrowser = false)

        val result = testee.evaluate(
            MatchingAttribute.DefaultBrowser(value = true)
        )

        assertEquals(Result.Fail, result)
    }

    // EmailEnabled
    @Test
    fun whenEmailEnabledMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(emailEnabled = true)

        val result = testee.evaluate(
            MatchingAttribute.EmailEnabled(value = true)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenEmailEnabledDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(emailEnabled = false)

        val result = testee.evaluate(
            MatchingAttribute.EmailEnabled(value = true)
        )

        assertEquals(Result.Fail, result)
    }

    // SearchCount
    @Test
    fun whenSearchCountMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(value = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenSearchCountDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(value = 15)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenSearchCountEqualOrLowerThanMaxThenReturnMatch() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(max = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenSearchCountGreaterThanMaxThenReturnFail() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(max = 5)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenSearchCountEqualOrGreaterThanMinThenReturnMatch() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(min = 10)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenSearchCountLowerThanMinThenReturnFail() = runBlocking {
        givenBrowserProperties(searchCount = 1L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(min = 10)
        )

        assertEquals(Result.Fail, result)
    }

    @Test
    fun whenSearchCountInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(min = 10, max = 15)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenSearchCountNotInRangeThenReturnMatch() = runBlocking {
        givenBrowserProperties(searchCount = 10L)

        val result = testee.evaluate(
            MatchingAttribute.SearchCount(min = 3, max = 6)
        )

        assertEquals(Result.Fail, result)
    }

    // WidgetAdded
    @Test
    fun whenWidgetAddedMatchesThenReturnMatch() = runBlocking {
        givenBrowserProperties(widgetAdded = true)

        val result = testee.evaluate(
            MatchingAttribute.WidgetAdded(value = true)
        )

        assertEquals(Result.Match, result)
    }

    @Test
    fun whenWidgetAddedDoesNotMatchThenReturnFail() = runBlocking {
        givenBrowserProperties(widgetAdded = false)

        val result = testee.evaluate(
            MatchingAttribute.WidgetAdded(value = true)
        )

        assertEquals(Result.Fail, result)
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
        widgetAdded: Boolean = true
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
