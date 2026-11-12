/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import android.annotation.SuppressLint
import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion

/**
 * The user-agent client hints (`Sec-CH-UA*`) Google Chrome identified by a user agent string would send.
 */
data class PirClientHints(
    val chromeMajorVersion: String,
    val platform: String,
    val mobile: Boolean,
) {
    // Chrome user agents only carry the major version, so the full version mirrors that reduced form.
    val chromeFullVersion: String get() = "$chromeMajorVersion.0.0.0"
}

internal object PirClientHintsParser {

    /**
     * Returns the client hints matching [userAgent], or null when it is not a Google Chrome user agent on a known
     * platform. Only Chrome's exact user agent format is accepted, so any other browser keeps the WebView's own hints.
     */
    fun parse(userAgent: String): PirClientHints? {
        val (platformDetails, chromeMajorVersion, mobileToken) = CHROME_USER_AGENT.matchEntire(userAgent)?.destructured ?: return null
        val platform = platformOf(platformDetails) ?: return null
        return PirClientHints(chromeMajorVersion, platform, mobile = mobileToken.isNotEmpty())
    }

    private fun platformOf(platformDetails: String): String? = when {
        platformDetails.contains("Windows NT") -> "Windows"
        platformDetails.contains("CrOS") -> "Chrome OS"
        platformDetails.contains("Android") -> "Android"
        platformDetails.contains("Macintosh") -> "macOS"
        platformDetails.contains("Linux") -> "Linux"
        else -> null
    }

    private val CHROME_USER_AGENT =
        Regex("""Mozilla/5\.0 \(([^)]+)\) AppleWebKit/537\.36 \(KHTML, like Gecko\) Chrome/(\d+)\.0\.0\.0 (Mobile )?Safari/537\.36""")
}

/**
 * Builds the WebView metadata for these hints. Setting any metadata also lets sites request the high-entropy hints,
 * so the device details are blanked to avoid revealing the real phone model and OS version behind the user agent.
 *
 * @param defaultMetadata the WebView's own metadata, whose GREASE brand is kept so the brand list stays well formed.
 * @param supportsFormFactors whether WebView supports `USER_AGENT_METADATA_FORM_FACTORS`, checked by the caller.
 */
@SuppressLint("RequiresFeature")
internal fun PirClientHints.toUserAgentMetadata(
    defaultMetadata: UserAgentMetadata,
    supportsFormFactors: Boolean,
): UserAgentMetadata {
    val greaseBrands = defaultMetadata.brandVersionList.filter { it.brand.contains(GREASE_BRAND_MARKER) }
    val brandVersions = listOf(CHROMIUM, GOOGLE_CHROME).map {
        BrandVersion.Builder()
            .setBrand(it)
            .setMajorVersion(chromeMajorVersion)
            .setFullVersion(chromeFullVersion)
            .build()
    }
    return UserAgentMetadata.Builder()
        .setBrandVersionList(brandVersions + greaseBrands)
        .setFullVersion(chromeFullVersion)
        .setPlatform(platform)
        .setMobile(mobile)
        .withoutDeviceDetails()
        .apply {
            if (supportsFormFactors) {
                setFormFactors(listOf(if (mobile) UserAgentMetadata.FORM_FACTOR_MOBILE else UserAgentMetadata.FORM_FACTOR_DESKTOP))
            }
        }
        .build()
}

/**
 * The WebView's own metadata with the device details blanked, used to undo broker hints on a reused WebView.
 */
internal fun UserAgentMetadata.withoutDeviceDetails(): UserAgentMetadata =
    UserAgentMetadata.Builder(this).withoutDeviceDetails().build()

private fun UserAgentMetadata.Builder.withoutDeviceDetails(): UserAgentMetadata.Builder =
    setPlatformVersion("")
        .setArchitecture("")
        .setModel("")
        .setBitness(UserAgentMetadata.BITNESS_DEFAULT)
        .setWow64(false)

private const val CHROMIUM = "Chromium"
private const val GOOGLE_CHROME = "Google Chrome"
private const val GREASE_BRAND_MARKER = "Brand"
