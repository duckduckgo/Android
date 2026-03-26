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

package com.duckduckgo.remote.messaging.impl.models

import com.duckduckgo.remote.messaging.api.MatchingAttribute
import logcat.LogPriority.INFO
import logcat.logcat
import java.util.*

data class Locale(
    override val value: List<String> = emptyList(),
    val fallback: Boolean? = null,
) : MatchingAttribute, StringArrayMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        if (this == Locale()) return false
        return (this as StringArrayMatchingAttribute).matches(matchingValue)
    }
}

data class Api(
    override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeIntMatchingAttribute, IntMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        if (this == Api()) return false

        if (this.value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
            return (this as IntMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeIntMatchingAttribute).matches(matchingValue)
    }
}

data class WebView(
    override val min: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    override val max: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeStringMatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        if (this == WebView()) return false
        if (value != MATCHING_ATTR_STRING_DEFAULT_VALUE) {
            return (this as StringMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeStringMatchingAttribute).matches(matchingValue)
    }
}

data class Flavor(
    override val value: List<String> = emptyList(),
    val fallback: Boolean? = null,
) : MatchingAttribute, StringArrayMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        if (this == Flavor()) return false
        return (this as StringArrayMatchingAttribute).matches(matchingValue)
    }
}

data class AppId(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class AppVersion(
    override val min: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    override val max: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeStringMatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        if (this == AppVersion()) return false
        if (value != MATCHING_ATTR_STRING_DEFAULT_VALUE) {
            return (this as StringMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeStringMatchingAttribute).matches(matchingValue)
    }
}

data class Atb(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class AppAtb(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class SearchAtb(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class ExpVariant(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class InstalledGPlay(
    override val value: Boolean,
    val fallback: Boolean? = null,
) : MatchingAttribute, BooleanMatchingAttribute {
    fun matches(matchingValue: Boolean): Boolean? {
        return (this as BooleanMatchingAttribute).matches(matchingValue)
    }
}

data class DefaultBrowser(
    override val value: Boolean,
    val fallback: Boolean? = null,
) : MatchingAttribute, BooleanMatchingAttribute {
    fun matches(matchingValue: Boolean): Boolean? {
        return (this as BooleanMatchingAttribute).matches(matchingValue)
    }
}

data class EmailEnabled(
    override val value: Boolean,
    val fallback: Boolean? = null,
) : MatchingAttribute, BooleanMatchingAttribute {
    fun matches(matchingValue: Boolean): Boolean? {
        return (this as BooleanMatchingAttribute).matches(matchingValue)
    }
}

data class WidgetAdded(
    override val value: Boolean,
    val fallback: Boolean? = null,
) : MatchingAttribute, BooleanMatchingAttribute {
    fun matches(matchingValue: Boolean): Boolean? {
        return (this as BooleanMatchingAttribute).matches(matchingValue)
    }
}

data class SearchCount(
    override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeIntMatchingAttribute, IntMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        if (this == SearchCount()) return false
        if (value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
            return (this as IntMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeIntMatchingAttribute).matches(matchingValue)
    }
}

data class Bookmarks(
    override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeIntMatchingAttribute, IntMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        if (this == Bookmarks()) return false
        if (value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
            return (this as IntMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeIntMatchingAttribute).matches(matchingValue)
    }
}

data class Favorites(
    override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeIntMatchingAttribute, IntMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        if (this == Favorites()) return false
        if (value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
            return (this as IntMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeIntMatchingAttribute).matches(matchingValue)
    }
}

data class AppTheme(
    override val value: String = MATCHING_ATTR_STRING_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, StringMatchingAttribute {
    fun matches(matchingValue: String): Boolean? {
        return (this as StringMatchingAttribute).matches(matchingValue)
    }
}

data class DaysSinceInstalled(
    override val min: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val max: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    override val value: Int = MATCHING_ATTR_INT_DEFAULT_VALUE,
    val fallback: Boolean? = null,
) : MatchingAttribute, RangeIntMatchingAttribute, IntMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        if (this == DaysSinceInstalled()) return false

        if (value != MATCHING_ATTR_INT_DEFAULT_VALUE) {
            return (this as IntMatchingAttribute).matches(matchingValue)
        }
        return (this as RangeIntMatchingAttribute).matches(matchingValue)
    }
}

data class DaysUsedSince(
    override val since: Date,
    override val value: Int,
    val fallback: Boolean? = null,
) : MatchingAttribute, DateMatchingAttribute {
    fun matches(matchingValue: Int): Boolean? {
        return (this as DateMatchingAttribute).matches(matchingValue)
    }
}

data class Unknown(val fallback: Boolean?) : MatchingAttribute {
    fun matches(matchingValue: Unit): Boolean? {
        return fallback
    }
}

interface RangeIntMatchingAttribute {
    val min: Int
    val max: Int
}

interface RangeStringMatchingAttribute {
    val min: String
    val max: String
}

interface BooleanMatchingAttribute {
    val value: Boolean
}

interface StringMatchingAttribute {
    val value: String
}

interface IntMatchingAttribute {
    val value: Int
}

interface StringArrayMatchingAttribute {
    val value: List<String>
}

interface DateMatchingAttribute {
    val since: Date
    val value: Int
}

fun StringArrayMatchingAttribute.matches(value: String): Boolean? {
    this.value.find { it.equals(value, true) } ?: return false
    return true
}

fun BooleanMatchingAttribute.matches(value: Boolean): Boolean? {
    return (this.value == value)
}

fun IntMatchingAttribute.matches(value: Int): Boolean {
    return (this.value == value)
}

fun RangeIntMatchingAttribute.matches(value: Int): Boolean {
    if ((this.min.isDefaultValue() || value >= this.min) &&
        (this.max.isDefaultValue() || value <= this.max)
    ) {
        return true
    }

    return false
}

fun DateMatchingAttribute.matches(value: Int): Boolean {
    if ((this.value.isDefaultValue() || value == this.value)) {
        return true
    }
    return false
}

fun StringMatchingAttribute.matches(value: String): Boolean? {
    return this.value.equals(value, ignoreCase = true)
}

fun RangeStringMatchingAttribute.matches(value: String): Boolean? {
    logcat(INFO) { "RMF: device value: $value" }
    if (!value.matches(Regex("[0-9]+(\\.[0-9]+)*"))) return false

    val versionAsIntList = value.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val minVersionAsIntList = this.min.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
    val maxVersionAsIntList = this.max.split(".").filter { it.isNotEmpty() }.map { it.toInt() }

    if (versionAsIntList.isEmpty()) return false
    if (versionAsIntList.compareTo(minVersionAsIntList) <= -1) return false
    if (versionAsIntList.compareTo(maxVersionAsIntList) >= 1) return false

    return true
}

private fun List<Int>.compareTo(other: List<Int>): Int {
    val otherSize = other.size

    for (index in this.indices) {
        if (index > otherSize - 1) return 0
        val value = this[index]
        if (value < other[index]) return -1
        if (value > other[index]) return 1
    }

    return 0
}

private fun String.isDefaultValue() = this.isEmpty()
private fun Int.isDefaultValue() = this == -1

@Suppress("UNCHECKED_CAST")
internal fun Any?.toStringList(): List<String> = this?.let { it as List<String> } ?: emptyList()

internal fun Any?.toIntOrDefault(default: Int): Int = when {
    this == null -> default
    this is Double -> this.toInt()
    this is Long -> this.toInt()
    else -> this as Int
}

internal fun Any?.toStringOrDefault(default: String): String = this?.let { it as String } ?: default

internal fun java.util.Locale.asJsonFormat() = "$language-$country"

const val MATCHING_ATTR_INT_DEFAULT_VALUE = -1
const val MATCHING_ATTR_STRING_DEFAULT_VALUE = ""
