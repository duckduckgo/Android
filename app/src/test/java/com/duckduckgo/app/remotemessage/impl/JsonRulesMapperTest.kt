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

package com.duckduckgo.app.remotemessage.impl

import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JsonRulesMapperTest(private val testCase: TestCase) {

    private val testee = JsonRulesMapper()

    @Test
    fun whenJsonMatchingAttributeThenReturnMatchingAttribute() {
        val matchingRule = testee.map(listOf(testCase.jsonMatchingRule))

        assertEquals(testCase.matchingRules, matchingRule)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters()
        fun parameters() = arrayOf(
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute(value = listOf("ES"))),
                    Pair("flavor", JsonMatchingAttribute(value = emptyList<String>()))
                ), matchingRule(
                    MatchingAttribute.Locale(value = listOf("ES"), fallback = null),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute(value = listOf("ES"), fallback = true)),
                    Pair("flavor", JsonMatchingAttribute(value = emptyList<String>(), fallback = false))
                ), matchingRule(
                    MatchingAttribute.Locale(value = listOf("ES"), fallback = true),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = false)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute(fallback = true)),
                    Pair("flavor", JsonMatchingAttribute(fallback = false))
                ), matchingRule(
                    MatchingAttribute.Locale(value = emptyList(), fallback = true),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = false)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute()),
                    Pair("flavor", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.Locale(value = emptyList(), fallback = null),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute(value = "wrong")),
                    Pair("flavor", JsonMatchingAttribute(value = false))
                ), matchingRule(
                    MatchingAttribute.Locale(value = emptyList(), fallback = null),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("locale", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("flavor", JsonMatchingAttribute(value = false, fallback = true))
                ), matchingRule(
                    MatchingAttribute.Locale(value = emptyList(), fallback = true),
                    MatchingAttribute.Flavor(value = emptyList(), fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(min = 20, max = 28)),
                    Pair("searchCount", JsonMatchingAttribute(min = 20, max = 28)),
                    Pair("bookmarks", JsonMatchingAttribute(min = 20, max = 28)),
                    Pair("favorites", JsonMatchingAttribute(min = 20, max = 28)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20, max = 28))
                ), matchingRule(
                    MatchingAttribute.Api(min = 20, max = 28, fallback = null),
                    MatchingAttribute.SearchCount(min = 20, max = 28, fallback = null),
                    MatchingAttribute.Bookmarks(min = 20, max = 28, fallback = null),
                    MatchingAttribute.Favorites(min = 20, max = 28, fallback = null),
                    MatchingAttribute.DaysSinceInstalled(min = 20, max = 28, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(max = 28)),
                    Pair("searchCount", JsonMatchingAttribute(max = 28)),
                    Pair("bookmarks", JsonMatchingAttribute(max = 28)),
                    Pair("favorites", JsonMatchingAttribute(max = 28)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(max = 28))
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = 28, fallback = null),
                    MatchingAttribute.SearchCount(min = -1, max = 28, fallback = null),
                    MatchingAttribute.Bookmarks(min = -1, max = 28, fallback = null),
                    MatchingAttribute.Favorites(min = -1, max = 28, fallback = null),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = 28, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(min = 20)),
                    Pair("searchCount", JsonMatchingAttribute(min = 20)),
                    Pair("bookmarks", JsonMatchingAttribute(min = 20)),
                    Pair("favorites", JsonMatchingAttribute(min = 20)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20))
                ), matchingRule(
                    MatchingAttribute.Api(min = 20, max = -1, fallback = null),
                    MatchingAttribute.SearchCount(min = 20, max = -1, fallback = null),
                    MatchingAttribute.Bookmarks(min = 20, max = -1, fallback = null),
                    MatchingAttribute.Favorites(min = 20, max = -1, fallback = null),
                    MatchingAttribute.DaysSinceInstalled(min = 20, max = -1, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                    Pair("searchCount", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                    Pair("bookmarks", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                    Pair("favorites", JsonMatchingAttribute(min = 20, max = 28, fallback = true)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(min = 20, max = 28, fallback = true))
                ), matchingRule(
                    MatchingAttribute.Api(min = 20, max = 28, fallback = true),
                    MatchingAttribute.SearchCount(min = 20, max = 28, fallback = true),
                    MatchingAttribute.Bookmarks(min = 20, max = 28, fallback = true),
                    MatchingAttribute.Favorites(min = 20, max = 28, fallback = true),
                    MatchingAttribute.DaysSinceInstalled(min = 20, max = 28, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("searchCount", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("bookmarks", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("favorites", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(value = "wrong", fallback = true))
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = -1, fallback = true),
                    MatchingAttribute.SearchCount(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Bookmarks(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Favorites(min = -1, max = -1, fallback = true),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = -1, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(min = "wrong", fallback = true)),
                    Pair("searchCount", JsonMatchingAttribute(min = "wrong", fallback = true)),
                    Pair("bookmarks", JsonMatchingAttribute(min = "wrong", fallback = true)),
                    Pair("favorites", JsonMatchingAttribute(min = "wrong", fallback = true)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(min = "wrong", fallback = true))
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = -1, fallback = true),
                    MatchingAttribute.SearchCount(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Bookmarks(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Favorites(min = -1, max = -1, fallback = true),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = -1, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(value = "wrong")),
                    Pair("searchCount", JsonMatchingAttribute(value = "wrong")),
                    Pair("bookmarks", JsonMatchingAttribute(value = "wrong")),
                    Pair("favorites", JsonMatchingAttribute(value = "wrong")),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(value = "wrong"))
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = -1, fallback = null),
                    MatchingAttribute.SearchCount(min = -1, max = -1, fallback = null),
                    MatchingAttribute.Bookmarks(min = -1, max = -1, fallback = null),
                    MatchingAttribute.Favorites(min = -1, max = -1, fallback = null),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = -1, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute()),
                    Pair("searchCount", JsonMatchingAttribute()),
                    Pair("bookmarks", JsonMatchingAttribute()),
                    Pair("favorites", JsonMatchingAttribute()),
                    Pair("daysSinceInstalled", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = -1, fallback = null),
                    MatchingAttribute.SearchCount(min = -1, max = -1, fallback = null),
                    MatchingAttribute.Bookmarks(min = -1, max = -1, fallback = null),
                    MatchingAttribute.Favorites(min = -1, max = -1, fallback = null),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = -1, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("osApi", JsonMatchingAttribute(fallback = true)),
                    Pair("searchCount", JsonMatchingAttribute(fallback = true)),
                    Pair("bookmarks", JsonMatchingAttribute(fallback = true)),
                    Pair("favorites", JsonMatchingAttribute(fallback = true)),
                    Pair("daysSinceInstalled", JsonMatchingAttribute(fallback = true))
                ), matchingRule(
                    MatchingAttribute.Api(min = -1, max = -1, fallback = true),
                    MatchingAttribute.SearchCount(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Bookmarks(min = -1, max = -1, fallback = true),
                    MatchingAttribute.Favorites(min = -1, max = -1, fallback = true),
                    MatchingAttribute.DaysSinceInstalled(min = -1, max = -1, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(min = "20", max = "28")),
                    Pair("appVersion", JsonMatchingAttribute(min = "20", max = "28"))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "20", max = "28", fallback = null),
                    MatchingAttribute.AppVersion(min = "20", max = "28", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(min = "20")),
                    Pair("appVersion", JsonMatchingAttribute(min = "20"))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "20", max = "", fallback = null),
                    MatchingAttribute.AppVersion(min = "20", max = "", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(max = "28")),
                    Pair("appVersion", JsonMatchingAttribute(max = "28"))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "", max = "28", fallback = null),
                    MatchingAttribute.AppVersion(min = "", max = "28", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(min = "20", max = "28", fallback = true)),
                    Pair("appVersion", JsonMatchingAttribute(min = "20", max = "28", fallback = true))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "20", max = "28", fallback = true),
                    MatchingAttribute.AppVersion(min = "20", max = "28", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(value = "wrong")),
                    Pair("appVersion", JsonMatchingAttribute(value = "wrong"))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "", max = "", fallback = null),
                    MatchingAttribute.AppVersion(min = "", max = "", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("appVersion", JsonMatchingAttribute(value = "wrong", fallback = true))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "", max = "", fallback = true),
                    MatchingAttribute.AppVersion(min = "", max = "", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute()),
                    Pair("appVersion", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.WebView(min = "", max = "", fallback = null),
                    MatchingAttribute.AppVersion(min = "", max = "", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("webview", JsonMatchingAttribute(fallback = true)),
                    Pair("appVersion", JsonMatchingAttribute(fallback = true))
                ), matchingRule(
                    MatchingAttribute.WebView(min = "", max = "", fallback = true),
                    MatchingAttribute.AppVersion(min = "", max = "", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute(value = true)),
                    Pair("defaultBrowser", JsonMatchingAttribute(value = true)),
                    Pair("emailEnabled", JsonMatchingAttribute(value = true)),
                    Pair("widgetAdded", JsonMatchingAttribute(value = true))
                ), matchingRule(
                    MatchingAttribute.InstalledGPlay(value = true),
                    MatchingAttribute.DefaultBrowser(value = true),
                    MatchingAttribute.EmailEnabled(value = true),
                    MatchingAttribute.WidgetAdded(value = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute(value = true, fallback = true)),
                    Pair("defaultBrowser", JsonMatchingAttribute(value = true, fallback = true)),
                    Pair("emailEnabled", JsonMatchingAttribute(value = true, fallback = true)),
                    Pair("widgetAdded", JsonMatchingAttribute(value = true, fallback = true))
                ), matchingRule(
                    MatchingAttribute.InstalledGPlay(value = true, fallback = true),
                    MatchingAttribute.DefaultBrowser(value = true, fallback = true),
                    MatchingAttribute.EmailEnabled(value = true, fallback = true),
                    MatchingAttribute.WidgetAdded(value = true, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute(value = "wrong")),
                    Pair("defaultBrowser", JsonMatchingAttribute(value = "wrong")),
                    Pair("emailEnabled", JsonMatchingAttribute(value = "wrong")),
                    Pair("widgetAdded", JsonMatchingAttribute(value = "wrong"))
                ), matchingRule(
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("defaultBrowser", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("emailEnabled", JsonMatchingAttribute(value = "wrong", fallback = true)),
                    Pair("widgetAdded", JsonMatchingAttribute(value = "wrong", fallback = true))
                ), matchingRule(
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute()),
                    Pair("defaultBrowser", JsonMatchingAttribute()),
                    Pair("emailEnabled", JsonMatchingAttribute()),
                    Pair("widgetAdded", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("installedGPlay", JsonMatchingAttribute(fallback = true)),
                    Pair("defaultBrowser", JsonMatchingAttribute(fallback = true)),
                    Pair("emailEnabled", JsonMatchingAttribute(fallback = true)),
                    Pair("widgetAdded", JsonMatchingAttribute(fallback = true))
                ), matchingRule(
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true),
                    MatchingAttribute.Unknown(fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute(value = "com.duckduckgo.mobile.android.debug")),
                    Pair("atb", JsonMatchingAttribute(value = "v298-8")),
                    Pair("appAtb", JsonMatchingAttribute(value = "v298-8")),
                    Pair("searchAtb", JsonMatchingAttribute(value = "v298-8")),
                    Pair("expVariant", JsonMatchingAttribute(value = "zo")),
                    Pair("appTheme", JsonMatchingAttribute(value = "light"))
                ), matchingRule(
                    MatchingAttribute.AppId(value = "com.duckduckgo.mobile.android.debug"),
                    MatchingAttribute.Atb(value = "v298-8"),
                    MatchingAttribute.AppAtb(value = "v298-8"),
                    MatchingAttribute.SearchAtb(value = "v298-8"),
                    MatchingAttribute.ExpVariant(value = "zo"),
                    MatchingAttribute.AppTheme(value = "light")
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute(value = "com.duckduckgo.mobile.android.debug", fallback = true)),
                    Pair("atb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                    Pair("appAtb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                    Pair("searchAtb", JsonMatchingAttribute(value = "v298-8", fallback = true)),
                    Pair("expVariant", JsonMatchingAttribute(value = "zo", fallback = true)),
                    Pair("appTheme", JsonMatchingAttribute(value = "light", fallback = true))
                ), matchingRule(
                    MatchingAttribute.AppId(value = "com.duckduckgo.mobile.android.debug", fallback = true),
                    MatchingAttribute.Atb(value = "v298-8", fallback = true),
                    MatchingAttribute.AppAtb(value = "v298-8", fallback = true),
                    MatchingAttribute.SearchAtb(value = "v298-8", fallback = true),
                    MatchingAttribute.ExpVariant(value = "zo", fallback = true),
                    MatchingAttribute.AppTheme(value = "light", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute(value = false)),
                    Pair("atb", JsonMatchingAttribute(value = false)),
                    Pair("appAtb", JsonMatchingAttribute(value = false)),
                    Pair("searchAtb", JsonMatchingAttribute(value = false)),
                    Pair("expVariant", JsonMatchingAttribute(value = false)),
                    Pair("appTheme", JsonMatchingAttribute(value = false))
                ), matchingRule(
                    MatchingAttribute.AppId(value = "", fallback = null),
                    MatchingAttribute.Atb(value = "", fallback = null),
                    MatchingAttribute.AppAtb(value = "", fallback = null),
                    MatchingAttribute.SearchAtb(value = "", fallback = null),
                    MatchingAttribute.ExpVariant(value = "", fallback = null),
                    MatchingAttribute.AppTheme(value = "", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute(value = false, fallback = true)),
                    Pair("atb", JsonMatchingAttribute(value = false, fallback = true)),
                    Pair("appAtb", JsonMatchingAttribute(value = false, fallback = true)),
                    Pair("searchAtb", JsonMatchingAttribute(value = false, fallback = true)),
                    Pair("expVariant", JsonMatchingAttribute(value = false, fallback = true)),
                    Pair("appTheme", JsonMatchingAttribute(value = false, fallback = true))
                ), matchingRule(
                    MatchingAttribute.AppId(value = "", fallback = true),
                    MatchingAttribute.Atb(value = "", fallback = true),
                    MatchingAttribute.AppAtb(value = "", fallback = true),
                    MatchingAttribute.SearchAtb(value = "", fallback = true),
                    MatchingAttribute.ExpVariant(value = "", fallback = true),
                    MatchingAttribute.AppTheme(value = "", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute()),
                    Pair("atb", JsonMatchingAttribute()),
                    Pair("appAtb", JsonMatchingAttribute()),
                    Pair("searchAtb", JsonMatchingAttribute()),
                    Pair("expVariant", JsonMatchingAttribute()),
                    Pair("appTheme", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.AppId(value = "", fallback = null),
                    MatchingAttribute.Atb(value = "", fallback = null),
                    MatchingAttribute.AppAtb(value = "", fallback = null),
                    MatchingAttribute.SearchAtb(value = "", fallback = null),
                    MatchingAttribute.ExpVariant(value = "", fallback = null),
                    MatchingAttribute.AppTheme(value = "", fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("appId", JsonMatchingAttribute(fallback = true)),
                    Pair("atb", JsonMatchingAttribute(fallback = true)),
                    Pair("appAtb", JsonMatchingAttribute(fallback = true)),
                    Pair("searchAtb", JsonMatchingAttribute(fallback = true)),
                    Pair("expVariant", JsonMatchingAttribute(fallback = true)),
                    Pair("appTheme", JsonMatchingAttribute(fallback = true))
                ), matchingRule(
                    MatchingAttribute.AppId(value = "", fallback = true),
                    MatchingAttribute.Atb(value = "", fallback = true),
                    MatchingAttribute.AppAtb(value = "", fallback = true),
                    MatchingAttribute.SearchAtb(value = "", fallback = true),
                    MatchingAttribute.ExpVariant(value = "", fallback = true),
                    MatchingAttribute.AppTheme(value = "", fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute(since = "2020-08-09", value = 2))
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "2020-08-09", value = 2)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute(since = "2020-08-09", value = 2, fallback = true))
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "2020-08-09", value = 2, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute(since = "wrong", value = "wrong"))
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "wrong", value = -1, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute(since = "wrong", value = "wrong", fallback = true))
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "wrong", value = -1, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute())
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "", value = -1, fallback = null)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("daysUsedSince", JsonMatchingAttribute(fallback = true))
                ), matchingRule(
                    MatchingAttribute.DaysUsedSince(since = "", value = -1, fallback = true)
                )
            ),
            TestCase(
                givenJsonRule(
                    Pair("unknown", JsonMatchingAttribute(value = "test")),
                    Pair("unknown2", JsonMatchingAttribute(value = "test", fallback = true))
                ), matchingRule(
                    MatchingAttribute.Unknown(fallback = null),
                    MatchingAttribute.Unknown(fallback = true)
                )
            )
        )

        private fun givenJsonRule(vararg jsonMatchingAttribute: Pair<String, JsonMatchingAttribute>): JsonMatchingRule {
            return JsonMatchingRule(
                id = 5,
                attributes = jsonMatchingAttribute.toMap()
            )
        }

        private fun matchingRule(vararg matchingAttribute: MatchingAttribute): Map<Int, List<MatchingAttribute>> {
            return mapOf(Pair(5, matchingAttribute.toList()))
        }
    }

    data class TestCase(val jsonMatchingRule: JsonMatchingRule, val matchingRules: Map<Int, List<MatchingAttribute>>)
}