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

import com.duckduckgo.remote.messaging.api.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.api.JsonToMatchingAttributeMapper
import com.duckduckgo.remote.messaging.api.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.*
import com.duckduckgo.remote.messaging.impl.models.toIntOrDefault
import com.duckduckgo.remote.messaging.impl.models.toStringList
import com.duckduckgo.remote.messaging.impl.models.toStringOrDefault
import java.text.SimpleDateFormat
import logcat.LogPriority.INFO
import logcat.logcat

private val dateFormatter = SimpleDateFormat("yyyy-mm-dd")

@Suppress("UNCHECKED_CAST")
private val localeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Locale(
        value = jsonMatchingAttribute.value.toStringList(),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val osApiMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Api(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val webViewMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    WebView(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

@Suppress("UNCHECKED_CAST")
private val flavorMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Flavor(
        value = jsonMatchingAttribute.value.toStringList(),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appIdMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    AppId(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appVersionMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    AppVersion(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val atbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Atb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    AppAtb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val searchAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    SearchAtb(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val expVariantMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    ExpVariant(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val installedGPlayMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    InstalledGPlay(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val defaultBrowserMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    DefaultBrowser(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val emailEnabledMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    EmailEnabled(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val widgetAddedMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    WidgetAdded(
        value = jsonMatchingAttribute.value as Boolean,
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val searchCountMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    SearchCount(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val bookmarksMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Bookmarks(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val favoritesMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    Favorites(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val appThemeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    AppTheme(
        value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val daysSinceInstalledMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
    DaysSinceInstalled(
        value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
        fallback = jsonMatchingAttribute.fallback,
    )
}

private val daysUsedSinceMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
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
)

fun List<JsonMatchingRule>.mapToMatchingRules(
    matchingAttributeMappers: Set<JsonToMatchingAttributeMapper>,
): List<Rule> = this.map {
    Rule(
        id = it.id,
        targetPercentile = it.targetPercentile.map(),
        attributes = it.attributes?.map { attrs -> attrs.map(matchingAttributeMappers) }.orEmpty(),
    )
}

private fun JsonTargetPercentile?.map(): TargetPercentile? {
    if (this == null) return null
    return TargetPercentile(
        before = this.before ?: 1f,
    )
}

private fun Map.Entry<String, JsonMatchingAttribute>.map(matchingAttributeMappers: Set<JsonToMatchingAttributeMapper>): MatchingAttribute {
    return runCatching {
        matchingAttributeMappers.forEach {
            val matchingAttribute = it.map(this.key, this.value)
            if (matchingAttribute != null) return@runCatching matchingAttribute
        }
        attributesMappers[this.key]?.invoke(this.value) ?: Unknown(this.value.fallback)
    }.onFailure {
        logcat(INFO) { "RMF: error $it" }
    }.getOrDefault(Unknown(this.value.fallback))
}
