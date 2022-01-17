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

import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.JsonMatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.JsonMatchingRule
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_INT_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.MATCHING_ATTR_STRING_DEFAULT_VALUE
import com.duckduckgo.remote.messaging.impl.models.toIntOrDefault
import com.duckduckgo.remote.messaging.impl.models.toStringList
import com.duckduckgo.remote.messaging.impl.models.toStringOrDefault
import timber.log.Timber
import java.text.SimpleDateFormat

class JsonRulesMapper {

    private val dateFormatter = SimpleDateFormat("yyyy-mm-dd")

    @Suppress("UNCHECKED_CAST")
    private val localeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Locale(
            value = jsonMatchingAttribute.value.toStringList(),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val osApiMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Api(
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val webViewMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.WebView(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val flavorMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Flavor(
            value = jsonMatchingAttribute.value.toStringList(),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appIdMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppId(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appVersionMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppVersion(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val atbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Atb(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppAtb(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val searchAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.SearchAtb(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val expVariantMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.ExpVariant(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val installedGPlayMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.InstalledGPlay(
            value = jsonMatchingAttribute.value as Boolean,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val defaultBrowserMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.DefaultBrowser(
            value = jsonMatchingAttribute.value as Boolean,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val emailEnabledMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.EmailEnabled(
            value = jsonMatchingAttribute.value as Boolean,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val widgetAddedMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.WidgetAdded(
            value = jsonMatchingAttribute.value as Boolean,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val searchCountMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.SearchCount(
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val bookmarksMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Bookmarks(
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val favoritesMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Favorites(
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appThemeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppTheme(
            value = jsonMatchingAttribute.value.toStringOrDefault(MATCHING_ATTR_STRING_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val daysSinceInstalledMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.DaysSinceInstalled(
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            min = jsonMatchingAttribute.min.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            max = jsonMatchingAttribute.max.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val daysUsedSinceMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.DaysUsedSince(
            since = dateFormatter.parse(jsonMatchingAttribute.since as String)!!,
            value = jsonMatchingAttribute.value.toIntOrDefault(MATCHING_ATTR_INT_DEFAULT_VALUE),
            fallback = jsonMatchingAttribute.fallback
        )
    }

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
        Pair("daysUsedSince", daysUsedSinceMapper)
    )

    fun map(jsonRules: List<JsonMatchingRule>): Map<Int, List<MatchingAttribute>> = jsonRules
        .map { Pair(it.id, it.attributes.map { attrs -> attrs.map() }) }.toMap()

    private fun Map.Entry<String, JsonMatchingAttribute>.map(): MatchingAttribute {
        return runCatching {
            attributesMappers[this.key]?.invoke(this.value) ?: MatchingAttribute.Unknown(this.value.fallback)
        }.onFailure {
            Timber.i("RMF: error $it")
        }.getOrDefault(MatchingAttribute.Unknown(this.value.fallback))
    }
}
