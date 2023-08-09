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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.*
import com.duckduckgo.remote.messaging.impl.models.toIntOrDefault
import com.duckduckgo.remote.messaging.impl.models.toStringList
import com.duckduckgo.remote.messaging.impl.models.toStringOrDefault
import java.text.SimpleDateFormat
import timber.log.Timber

private val dateFormatter = SimpleDateFormat("yyyy-mm-dd")

@Suppress("UNCHECKED_CAST")
private val localeMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    Locale(
        value = jsonMatchingAttribute.value.toStringList(),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val osApiMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    Api(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val webViewMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    WebView(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

@Suppress("UNCHECKED_CAST")
private val flavorMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    Flavor(
        value = jsonMatchingAttribute.value.toStringList(),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appIdMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    AppId(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appVersionMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    AppVersion(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val atbMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    Atb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    AppAtb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val searchAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    SearchAtb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val expVariantMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    ExpVariant(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val installedGPlayMapper: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    InstalledGPlay(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val defaultBrowserMapper: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    DefaultBrowser(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val emailEnabledMapper: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    EmailEnabled(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appTrackingProtectionOnboarded: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    AppTpOnboarded(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val networkProtectionOnboarded: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    NetPOnboarded(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val widgetAddedMapper: (JsonMatchingAttribute) -> MatchingAttribute<Boolean> = { jsonMatchingAttribute ->
    WidgetAdded(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val searchCountMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    SearchCount(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val bookmarksMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    Bookmarks(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val favoritesMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    Favorites(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appThemeMapper: (JsonMatchingAttribute) -> MatchingAttribute<String> = { jsonMatchingAttribute ->
    AppTheme(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val daysSinceInstalledMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    DaysSinceInstalled(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val daysUsedSinceMapper: (JsonMatchingAttribute) -> MatchingAttribute<Int> = { jsonMatchingAttribute ->
    DaysUsedSince(
        since = dateFormatter.parse(jsonMatchingAttribute.since as String)!!,
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

// plugin point ?
private val attributesMappers = mapOf(
    Pair("locale", localeMapper),
    Pair("osApi", osApiMapper),
    Pair("webview", webViewMapper),
    Pair("flavor", flavorMapper),
    Pair("appId", appIdMapper),
    Pair("appVersion", appVersionMapper),
    Pair("atb", atbMapper),
    Pair("appAtb", appAtbMapper),
    Pair("searchAtb", searchAtbMapper),
    Pair("expVariant", expVariantMapper),
    Pair("installedGPlay", installedGPlayMapper),
    Pair("defaultBrowser", defaultBrowserMapper),
    Pair("emailEnabled", emailEnabledMapper),
    Pair("widgetAdded", widgetAddedMapper),
    Pair("searchCount", searchCountMapper),
    Pair("bookmarks", bookmarksMapper),
    Pair("favorites", favoritesMapper),
    Pair("appTheme", appThemeMapper),
    Pair("daysSinceInstalled", daysSinceInstalledMapper),
    Pair("daysUsedSince", daysUsedSinceMapper),
    Pair("atpOnboarded", appTrackingProtectionOnboarded),
    Pair("netpOnboarded", networkProtectionOnboarded),
)

fun List<JsonMatchingRule>.mapToMatchingRules(): Map<Int, List<MatchingAttribute<*>>> = this.map {
    Pair(it.id, it.attributes.map { attrs -> attrs.map() })
}.toMap()

private fun Map.Entry<String, JsonMatchingAttribute>.map(): MatchingAttribute<*> {
    return runCatching {
        attributesMappers[this.key]?.invoke(this.value) ?: Unknown(this.value.fallback)
    }.onFailure {
        Timber.i("RMF: error $it")
    }.getOrDefault(Unknown(this.value.fallback))
}
