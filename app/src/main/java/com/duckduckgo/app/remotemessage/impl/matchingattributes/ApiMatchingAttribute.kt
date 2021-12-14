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

package com.duckduckgo.app.remotemessage.impl.matchingattributes

import com.duckduckgo.app.remotemessage.impl.MatchingAttributePlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class LocaleAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "locale"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Locale>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class ApiMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "osApi"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Api>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class WebViewMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "webview"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.WebView>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class FlavorMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "flavor"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Flavor>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class AppIdMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "appId"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.AppId>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class AppVersionMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "appVersion"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.AppVersion>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class AtbMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "atb"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Atb>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class AppAtbMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "appAtb"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.AppAtb>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class SearchAtbMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "searchAtb"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.SearchAtb>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class ExpVariantMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "expVariant"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.ExpVariant>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class InstalledGPlayMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "installedGPlay"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.InstalledGPlay>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class DefaultBrowserMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "defaultBrowser"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.DefaultBrowser>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class EmailEnabledMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "emailEnabled"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.EmailEnabled>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class WidgetAddedMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "widgetAdded"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.WidgetAdded>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class SearchCountMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "searchCount"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.SearchCount>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class BookmarksMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "bookmarks"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Bookmarks>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class FavoritesMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "favorites"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.Favorites>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class AppThemeMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "appTheme"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.AppTheme>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class DaysSinceInstalledMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "daysSinceInstalled"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.DaysSinceInstalled>(key, json, featureName)
}

@ContributesMultibinding(AppScope::class)
class DaysUsedSinceMatchingAttribute @Inject constructor() : MatchingAttributePlugin {
    private val featureName = "daysUsedSince"

    override fun parse(key: String, json: String) = parse<MatchingAttribute.DaysUsedSince>(key, json, featureName)
}

private inline fun <reified T : MatchingAttribute> parse(key: String, json: String, featureName: String): T? {
    if (key == featureName) {
        return parse(json)
    }
    return null
}

inline fun <reified T : MatchingAttribute> parse(json: String): T? {
    val moshi = Moshi.Builder().build()
    val jsonAdapter: JsonAdapter<T> = moshi.adapter(T::class.java)
    return jsonAdapter.fromJson(json)
}
