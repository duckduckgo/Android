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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.fixtures.FakeMatchingAttribute
import com.duckduckgo.remote.messaging.fixtures.jsonMatchingAttributeMappers
import com.duckduckgo.remote.messaging.impl.mappers.mapToMatchingRules
import com.duckduckgo.remote.messaging.impl.models.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.text.SimpleDateFormat

@RunWith(Parameterized::class)
class JsonRulesMapperTest(private val testCase: TestCase) {

    @Test
    fun whenJsonMatchingAttributeThenReturnMatchingAttribute() {
        val matchingRule = listOf(testCase.jsonMatchingRule).mapToMatchingRules(jsonMatchingAttributeMappers)

        assertEquals(testCase.matchingRules, matchingRule)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters()
        fun parameters() = arrayOf(
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("Fake", JsonMatchingAttribute(value = false)),
                        Pair("flavor", JsonMatchingAttribute(value = emptyList<String>())),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        FakeMatchingAttribute(value = false),
                        Flavor(value = emptyList(), fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute(value = listOf("ES"))),
                        Pair("flavor", JsonMatchingAttribute(value = emptyList<String>())),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Locale(value = listOf("ES"), fallback = null),
                        Flavor(value = emptyList(), fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute(value = listOf("ES"), fallback = true)),
                        Pair("flavor", JsonMatchingAttribute(value = emptyList<String>(), fallback = false)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Locale(value = listOf("ES"), fallback = true),
                        Flavor(value = emptyList(), fallback = false),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute(fallback = true)),
                        Pair("flavor", JsonMatchingAttribute(fallback = false)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Locale(value = emptyList(), fallback = true),
                        Flavor(value = emptyList(), fallback = false),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute()),
                        Pair("flavor", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Locale(value = emptyList(), fallback = null),
                        Flavor(value = emptyList(), fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute(value = "wrong")),
                        Pair("flavor", JsonMatchingAttribute(value = false)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("locale", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("flavor", JsonMatchingAttribute(value = false, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(value = 28)),
                        Pair("searchCount", JsonMatchingAttribute(value = 28)),
                        Pair("bookmarks", JsonMatchingAttribute(value = 28)),
                        Pair("favorites", JsonMatchingAttribute(value = 28)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(value = 28)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(value = 28, fallback = null),
                        SearchCount(value = 28, fallback = null),
                        Bookmarks(value = 28, fallback = null),
                        Favorites(value = 28, fallback = null),
                        DaysSinceInstalled(value = 28, fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(value = 15, min = 20, max = 28)),
                        Pair("searchCount", JsonMatchingAttribute(value = 15, min = 20, max = 28)),
                        Pair("bookmarks", JsonMatchingAttribute(value = 15, min = 20, max = 28)),
                        Pair("favorites", JsonMatchingAttribute(value = 15, min = 20, max = 28)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(value = 15, min = 20, max = 28)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(value = 15, min = 20, max = 28, fallback = null),
                        SearchCount(value = 15, min = 20, max = 28, fallback = null),
                        Bookmarks(value = 15, min = 20, max = 28, fallback = null),
                        Favorites(value = 15, min = 20, max = 28, fallback = null),
                        DaysSinceInstalled(value = 15, min = 20, max = 28, fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(min = 20, max = 28)),
                        Pair("searchCount", JsonMatchingAttribute(min = 20, max = 28)),
                        Pair("bookmarks", JsonMatchingAttribute(min = 20, max = 28)),
                        Pair("favorites", JsonMatchingAttribute(min = 20, max = 28)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20, max = 28)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(min = 20, max = 28, fallback = null),
                        SearchCount(min = 20, max = 28, fallback = null),
                        Bookmarks(min = 20, max = 28, fallback = null),
                        Favorites(min = 20, max = 28, fallback = null),
                        DaysSinceInstalled(min = 20, max = 28, fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(max = 28)),
                        Pair("searchCount", JsonMatchingAttribute(max = 28)),
                        Pair("bookmarks", JsonMatchingAttribute(max = 28)),
                        Pair("favorites", JsonMatchingAttribute(max = 28)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(max = 28)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(min = -1, max = 28, fallback = null),
                        SearchCount(min = -1, max = 28, fallback = null),
                        Bookmarks(min = -1, max = 28, fallback = null),
                        Favorites(min = -1, max = 28, fallback = null),
                        DaysSinceInstalled(min = -1, max = 28, fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(min = 20)),
                        Pair("searchCount", JsonMatchingAttribute(min = 20)),
                        Pair("bookmarks", JsonMatchingAttribute(min = 20)),
                        Pair("favorites", JsonMatchingAttribute(min = 20)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(min = 20, max = -1, fallback = null),
                        SearchCount(min = 20, max = -1, fallback = null),
                        Bookmarks(min = 20, max = -1, fallback = null),
                        Favorites(min = 20, max = -1, fallback = null),
                        DaysSinceInstalled(min = 20, max = -1, fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                        Pair("searchCount", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                        Pair("bookmarks", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                        Pair("favorites", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(min = 20, max = 28, fallback = true),
                        SearchCount(min = 20, max = 28, fallback = true),
                        Bookmarks(min = 20, max = 28, fallback = true),
                        Favorites(min = 20, max = 28, fallback = true),
                        DaysSinceInstalled(min = 20, max = 28, fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("searchCount", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("bookmarks", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("favorites", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(min = "wrong", fallback = true)),
                        Pair("searchCount", JsonMatchingAttribute(min = "wrong", fallback = true)),
                        Pair("bookmarks", JsonMatchingAttribute(min = "wrong", fallback = true)),
                        Pair("favorites", JsonMatchingAttribute(min = "wrong", fallback = true)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(min = "wrong", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(value = "wrong")),
                        Pair("searchCount", JsonMatchingAttribute(value = "wrong")),
                        Pair("bookmarks", JsonMatchingAttribute(value = "wrong")),
                        Pair("favorites", JsonMatchingAttribute(value = "wrong")),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(value = "wrong")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute()),
                        Pair("searchCount", JsonMatchingAttribute()),
                        Pair("bookmarks", JsonMatchingAttribute()),
                        Pair("favorites", JsonMatchingAttribute()),
                        Pair("daysSinceInstalled", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(),
                        SearchCount(),
                        Bookmarks(),
                        Favorites(),
                        DaysSinceInstalled(),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("osApi", JsonMatchingAttribute(fallback = true)),
                        Pair("searchCount", JsonMatchingAttribute(fallback = true)),
                        Pair("bookmarks", JsonMatchingAttribute(fallback = true)),
                        Pair("favorites", JsonMatchingAttribute(fallback = true)),
                        Pair("daysSinceInstalled", JsonMatchingAttribute(fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Api(fallback = true),
                        SearchCount(fallback = true),
                        Bookmarks(fallback = true),
                        Favorites(fallback = true),
                        DaysSinceInstalled(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(value = "28")),
                        Pair("appVersion", JsonMatchingAttribute(value = "28")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(value = "28", fallback = null),
                        AppVersion(value = "28", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(value = "15", min = "20", max = "28")),
                        Pair("appVersion", JsonMatchingAttribute(value = "15", min = "20", max = "28")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(value = "15", min = "20", max = "28", fallback = null),
                        AppVersion(value = "15", min = "20", max = "28", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(min = "20", max = "28")),
                        Pair("appVersion", JsonMatchingAttribute(min = "20", max = "28")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "20", max = "28", fallback = null),
                        AppVersion(min = "20", max = "28", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(min = "20")),
                        Pair("appVersion", JsonMatchingAttribute(min = "20")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "20", max = "", fallback = null),
                        AppVersion(min = "20", max = "", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(max = "28")),
                        Pair("appVersion", JsonMatchingAttribute(max = "28")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "", max = "28", fallback = null),
                        AppVersion(min = "", max = "28", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(min = "20", max = "28", fallback = true)),
                        Pair("appVersion", JsonMatchingAttribute(min = "20", max = "28", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "20", max = "28", fallback = true),
                        AppVersion(min = "20", max = "28", fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute()),
                        Pair("appVersion", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "", max = "", fallback = null),
                        AppVersion(min = "", max = "", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("webview", JsonMatchingAttribute(fallback = true)),
                        Pair("appVersion", JsonMatchingAttribute(fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        WebView(min = "", max = "", fallback = true),
                        AppVersion(min = "", max = "", fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute(value = true)),
                        Pair("defaultBrowser", JsonMatchingAttribute(value = true)),
                        Pair("emailEnabled", JsonMatchingAttribute(value = true)),
                        Pair("widgetAdded", JsonMatchingAttribute(value = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        InstalledGPlay(value = true),
                        DefaultBrowser(value = true),
                        EmailEnabled(value = true),
                        WidgetAdded(value = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute(value = true, fallback = true)),
                        Pair("defaultBrowser", JsonMatchingAttribute(value = true, fallback = true)),
                        Pair("emailEnabled", JsonMatchingAttribute(value = true, fallback = true)),
                        Pair("widgetAdded", JsonMatchingAttribute(value = true, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        InstalledGPlay(value = true, fallback = true),
                        DefaultBrowser(value = true, fallback = true),
                        EmailEnabled(value = true, fallback = true),
                        WidgetAdded(value = true, fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute(value = "wrong")),
                        Pair("defaultBrowser", JsonMatchingAttribute(value = "wrong")),
                        Pair("emailEnabled", JsonMatchingAttribute(value = "wrong")),
                        Pair("widgetAdded", JsonMatchingAttribute(value = "wrong")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("defaultBrowser", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("emailEnabled", JsonMatchingAttribute(value = "wrong", fallback = true)),
                        Pair("widgetAdded", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute()),
                        Pair("defaultBrowser", JsonMatchingAttribute()),
                        Pair("emailEnabled", JsonMatchingAttribute()),
                        Pair("widgetAdded", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("installedGPlay", JsonMatchingAttribute(fallback = true)),
                        Pair("defaultBrowser", JsonMatchingAttribute(fallback = true)),
                        Pair("emailEnabled", JsonMatchingAttribute(fallback = true)),
                        Pair("widgetAdded", JsonMatchingAttribute(fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute(value = "com.duckduckgo.mobile.android.debug")),
                        Pair("atb", JsonMatchingAttribute(value = "v298-8")),
                        Pair("appAtb", JsonMatchingAttribute(value = "v298-8")),
                        Pair("searchAtb", JsonMatchingAttribute(value = "v298-8")),
                        Pair("expVariant", JsonMatchingAttribute(value = "zo")),
                        Pair("appTheme", JsonMatchingAttribute(value = "light")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        AppId(value = "com.duckduckgo.mobile.android.debug"),
                        Atb(value = "v298-8"),
                        AppAtb(value = "v298-8"),
                        SearchAtb(value = "v298-8"),
                        ExpVariant(value = "zo"),
                        AppTheme(value = "light"),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute(value = "com.duckduckgo.mobile.android.debug", fallback = true)),
                        Pair("atb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                        Pair("appAtb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                        Pair("searchAtb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                        Pair("expVariant", JsonMatchingAttribute(value = "zo", fallback = true)),
                        Pair("appTheme", JsonMatchingAttribute(value = "light", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        AppId(value = "com.duckduckgo.mobile.android.debug", fallback = true),
                        Atb(value = "v298-8", fallback = true),
                        AppAtb(value = "v298-8", fallback = true),
                        SearchAtb(value = "v298-8", fallback = true),
                        ExpVariant(value = "zo", fallback = true),
                        AppTheme(value = "light", fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute(value = 15)),
                        Pair("atb", JsonMatchingAttribute(value = 15)),
                        Pair("appAtb", JsonMatchingAttribute(value = 15)),
                        Pair("searchAtb", JsonMatchingAttribute(value = 15)),
                        Pair("expVariant", JsonMatchingAttribute(value = 15)),
                        Pair("appTheme", JsonMatchingAttribute(value = 15)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute(value = 15, fallback = true)),
                        Pair("atb", JsonMatchingAttribute(value = 15, fallback = true)),
                        Pair("appAtb", JsonMatchingAttribute(value = 15, fallback = true)),
                        Pair("searchAtb", JsonMatchingAttribute(value = 15, fallback = true)),
                        Pair("expVariant", JsonMatchingAttribute(value = 15, fallback = true)),
                        Pair("appTheme", JsonMatchingAttribute(value = 15, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute()),
                        Pair("atb", JsonMatchingAttribute()),
                        Pair("appAtb", JsonMatchingAttribute()),
                        Pair("searchAtb", JsonMatchingAttribute()),
                        Pair("expVariant", JsonMatchingAttribute()),
                        Pair("appTheme", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        AppId(value = "", fallback = null),
                        Atb(value = "", fallback = null),
                        AppAtb(value = "", fallback = null),
                        SearchAtb(value = "", fallback = null),
                        ExpVariant(value = "", fallback = null),
                        AppTheme(value = "", fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("appId", JsonMatchingAttribute(fallback = true)),
                        Pair("atb", JsonMatchingAttribute(fallback = true)),
                        Pair("appAtb", JsonMatchingAttribute(fallback = true)),
                        Pair("searchAtb", JsonMatchingAttribute(fallback = true)),
                        Pair("expVariant", JsonMatchingAttribute(fallback = true)),
                        Pair("appTheme", JsonMatchingAttribute(fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        AppId(value = "", fallback = true),
                        Atb(value = "", fallback = true),
                        AppAtb(value = "", fallback = true),
                        SearchAtb(value = "", fallback = true),
                        ExpVariant(value = "", fallback = true),
                        AppTheme(value = "", fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(since = "2020-08-09", value = 2)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        DaysUsedSince(since = SimpleDateFormat("yyyy-mm-dd").parse("2020-08-09")!!, value = 2),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(since = "2020-08-09", value = 2, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        DaysUsedSince(since = SimpleDateFormat("yyyy-mm-dd").parse("2020-08-09")!!, value = 2, fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(since = "20200809", value = 2, fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(since = "wrong", value = "wrong")),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(since = "wrong", value = "wrong", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute()),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("daysUsedSince", JsonMatchingAttribute(fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    jsonMatchingAttribute = arrayOf(
                        Pair("unknown", JsonMatchingAttribute(value = "test")),
                        Pair("unknown2", JsonMatchingAttribute(value = "test", fallback = true)),
                    ),
                ),
                matchingRule(
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = true),
                    ),
                ),
            ),
            TestCase(
                givenJsonRule(
                    percentile = 0.4f,
                    jsonMatchingAttribute = arrayOf(
                        Pair("unknown", JsonMatchingAttribute(value = "test")),
                        Pair("unknown2", JsonMatchingAttribute(value = "test", fallback = true)),
                    ),
                ),
                matchingRule(
                    percentile = 0.4f,
                    matchingAttribute = arrayOf(
                        Unknown(fallback = null),
                        Unknown(fallback = true),
                    ),
                ),
            ),
        )

        private fun givenJsonRule(
            percentile: Float? = null,
            vararg jsonMatchingAttribute: Pair<String, JsonMatchingAttribute>,
        ): JsonMatchingRule {
            return JsonMatchingRule(
                id = 5,
                targetPercentile = JsonTargetPercentile(before = percentile),
                attributes = jsonMatchingAttribute.toMap(),
            )
        }

        private fun matchingRule(
            percentile: Float = 1f,
            vararg matchingAttribute: MatchingAttribute,
        ): List<Rule> {
            return listOf(Rule(id = 5, targetPercentile = TargetPercentile(before = percentile), attributes = matchingAttribute.toList()))
        }
    }

    data class TestCase(
        val jsonMatchingRule: JsonMatchingRule,
        val matchingRules: List<Rule>,
    )
}
