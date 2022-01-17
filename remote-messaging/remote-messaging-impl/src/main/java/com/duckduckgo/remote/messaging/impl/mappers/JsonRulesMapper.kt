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
import timber.log.Timber
import java.text.SimpleDateFormat

class JsonRulesMapper {

    private val dateFormatter = SimpleDateFormat("yyyy-mm-dd")

    @Suppress("UNCHECKED_CAST")
    private val localeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Locale(
            value = (jsonMatchingAttribute.value as? List<String>) ?: emptyList(),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val osApiMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Api(
            min = (jsonMatchingAttribute.min as? Int) ?: MIN_DEFAULT_VALUE,
            max = (jsonMatchingAttribute.max as? Int) ?: MAX_DEFAULT_VALUE,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val webViewMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.WebView(
            min = (jsonMatchingAttribute.min as? String) ?: "",
            max = (jsonMatchingAttribute.max as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val flavorMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Flavor(
            value = (jsonMatchingAttribute.value as? List<String>) ?: emptyList(),
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appIdMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppId(
            value = (jsonMatchingAttribute.value as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appVersionMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppVersion(
            min = (jsonMatchingAttribute.min as? String) ?: "",
            max = (jsonMatchingAttribute.max as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val atbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Atb(
            value = (jsonMatchingAttribute.value as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppAtb(
            value = (jsonMatchingAttribute.value as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val searchAtbMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.SearchAtb(
            value = (jsonMatchingAttribute.value as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val expVariantMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.ExpVariant(
            value = (jsonMatchingAttribute.value as? String) ?: "",
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
            min = (jsonMatchingAttribute.min as? Int) ?: MIN_DEFAULT_VALUE,
            max = (jsonMatchingAttribute.max as? Int) ?: MAX_DEFAULT_VALUE,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val bookmarksMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Bookmarks(
            min = (jsonMatchingAttribute.min as? Int) ?: MIN_DEFAULT_VALUE,
            max = (jsonMatchingAttribute.max as? Int) ?: MAX_DEFAULT_VALUE,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val favoritesMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.Favorites(
            min = (jsonMatchingAttribute.min as? Int) ?: MIN_DEFAULT_VALUE,
            max = (jsonMatchingAttribute.max as? Int) ?: MAX_DEFAULT_VALUE,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val appThemeMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.AppTheme(
            value = (jsonMatchingAttribute.value as? String) ?: "",
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val daysSinceInstalledMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.DaysSinceInstalled(
            min = (jsonMatchingAttribute.min as? Int) ?: MIN_DEFAULT_VALUE,
            max = (jsonMatchingAttribute.max as? Int) ?: MAX_DEFAULT_VALUE,
            fallback = jsonMatchingAttribute.fallback
        )
    }

    private val daysUsedSinceMapper: (JsonMatchingAttribute) -> MatchingAttribute = { jsonMatchingAttribute ->
        MatchingAttribute.DaysUsedSince(
            since = dateFormatter.parse(jsonMatchingAttribute.since as String)!!,
            value = (jsonMatchingAttribute.value as? Int) ?: -1,
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

    companion object {
        private const val MIN_DEFAULT_VALUE = -1
        private const val MAX_DEFAULT_VALUE = -1
    }
}
