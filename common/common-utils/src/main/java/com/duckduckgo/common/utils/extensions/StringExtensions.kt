/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.common.utils.extensions

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.text.Spanned
import androidx.core.content.ContextCompat
import java.util.*
import okhttp3.internal.publicsuffix.PublicSuffixDatabase

fun String.capitalizeFirstLetter() = this.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
}

fun String.html(context: Context): Spanned {
    return Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT, { htmlDrawable(context, it.toInt()) }, null)
}

private fun htmlDrawable(
    context: Context,
    resource: Int,
): Drawable? {
    return ContextCompat.getDrawable(context, resource)?.also {
        it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
    }
}

private const val HTTPS_PREFIX = "https://"
private const val WWW_PREFIX = "www."
private const val WWW_SUFFIX = "/"
private val publicSuffixDatabase = PublicSuffixDatabase()

fun String.websiteFromGeoLocationsApiOrigin(): String {
    val uri = Uri.parse(this)
    val host = uri.host ?: return this

    return host.takeIf { it.startsWith(WWW_PREFIX, ignoreCase = true) }
        ?.drop(WWW_PREFIX.length) ?: host
}

fun String.asLocationPermissionOrigin(): String {
    return HTTPS_PREFIX + this + WWW_SUFFIX
}

fun String.toTldPlusOne(): String? {
    return runCatching { publicSuffixDatabase.getEffectiveTldPlusOne(this) }.getOrNull()
}

/**
 * Compares the current semantic version string with the target semantic version string.
 *
 * The version strings can have an arbitrary number of parts separated by dots (e.g. "x.y.z").
 * Each part is expected to be an integer.
 * If any part of the version string is not a valid integer, the function returns null.
 *
 * @param targetVersion The version string to compare against.
 * @return 1 if the current version is greater, -1 if it is smaller, 0 if they are equal or null if the format is invalid.
 */
fun String.compareSemanticVersion(targetVersion: String): Int? {
    val versionParts = this.split(".")
    val targetParts = targetVersion.split(".")

    val maxLength = maxOf(versionParts.size, targetParts.size)

    for (i in 0 until maxLength) {
        val versionPart = versionParts.getOrElse(i) { "0" }.toIntOrNull() ?: return null
        val targetPart = targetParts.getOrElse(i) { "0" }.toIntOrNull() ?: return null

        if (versionPart != targetPart) return versionPart.compareTo(targetPart)
    }
    return 0
}


private const val NON_BREAKING_SPACE = '\u00A0'

/**
 * Prevents typographic widows by replacing the last space with a non-breaking space.
 *
 * A "widow" in typography refers to a single word that appears alone on the last line
 * of a paragraph or text block, which is considered poor typography as it creates
 * visual imbalance and awkward spacing.
 *
 * This function finds the last space character in the string and replaces it with
 * a non-breaking space (U+00A0) to ensure the last two words stay together on the
 * same line, preventing the final word from becoming orphaned.
 *
 * @return A new string with the last space replaced by a non-breaking space,
 *         or the original string if no space is found or if the space is at the end
 *
 * @see <a href="https://en.wikipedia.org/wiki/Widows_and_orphans">Widows and orphans typography</a>
 */
fun String.preventWidows(): String {
    val lastSpaceIndex = this.lastIndexOf(' ')
    // Ensure there is a space and it's not the last character
    if (lastSpaceIndex > 0 && lastSpaceIndex < this.length - 1) {
        val builder = StringBuilder(this)
        builder.setCharAt(lastSpaceIndex, NON_BREAKING_SPACE)
        return builder.toString()
    }
    return this
}
