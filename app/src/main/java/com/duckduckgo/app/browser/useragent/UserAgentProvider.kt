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

package com.duckduckgo.app.browser.useragent

import android.os.Build
import androidx.core.net.toUri
import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import com.duckduckgo.app.dev.settings.db.UAOverride
import com.duckduckgo.app.global.UriString
import com.duckduckgo.app.global.device.DeviceInfo

/**
 * Example Default User Agent (From Chrome):
 * Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36
 *
 * Example Default Desktop User Agent (From Chrome):
 * Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Safari/537.36
 */
class UserAgentProvider constructor(private val defaultUserAgent: String, private val device: DeviceInfo, private val devSettingsDataStore: DevSettingsDataStore) {

    private val baseAgent: String
    private val baseDesktopAgent: String
    private val safariComponent: String?
    private val applicationComponent = "DuckDuckGo/${device.majorAppVersion}"

    init {
        safariComponent = getSafariComponent()
        baseAgent = concatWithSpaces(mobilePrefix, getWebKitVersionOnwards(false))
        baseDesktopAgent = concatWithSpaces(desktopPrefix, getWebKitVersionOnwards(true))
    }

    /**
     * Returns, our custom UA, including our application component before Safari
     *
     * Modifies UA string to omits the user's device make and model and drops components that may casue breakages
     * If the user is requesting a desktop site, we add generic X11 Linux indicator, but include the real architecture
     * If the user is requesting a mobile site, we add Linux Android indicator, and include the real Android OS version
     * If the site breaks when our application component is included, we exclude it
     *
     * We include everything from the original UA string from AppleWebKit onwards (omitting if missing)
     */
    fun userAgent(url: String? = null, isDesktop: Boolean = false): String {
        if (devSettingsDataStore.overrideUA) {
            return when (devSettingsDataStore.selectedUA) {
                UAOverride.NO_APP_ID -> defaultDDGUserAgent(url, isDesktop).replace(applicationComponent, "")
                UAOverride.NO_VERSION -> defaultDDGUserAgent(url, isDesktop).replace(AgentRegex.version, "")
                UAOverride.DDG -> defaultDDGUserAgent(url, isDesktop)
                UAOverride.CHROME -> defaultDDGUserAgent(url, isDesktop).replace(applicationComponent, "").replace(AgentRegex.version, "")
                UAOverride.FIREFOX -> "Mozilla/5.0 (Android 11; Mobile; rv:94.0) Gecko/94.0 Firefox/94.0"
                UAOverride.WEBVIEW -> defaultUserAgent
            }
        }

        return defaultDDGUserAgent(url, isDesktop)
    }

    private fun defaultDDGUserAgent(url: String?, isDesktop: Boolean): String {
        val host = url?.toUri()?.host
        val omitApplicationComponent = if (host != null) sitesThatOmitApplication.any { UriString.sameOrSubdomain(host, it) } else false
        val omitVersionComponent = if (host != null) sitesThatOmitVersion.any { UriString.sameOrSubdomain(host, it) } else false
        val shouldUseDesktopAgent =
            if (url != null && host != null) {
                sitesThatShouldUseDesktopAgent.any { UriString.sameOrSubdomain(host, it.host) && !containsExcludedPath(url, it) }
            } else {
                false
            }
        var prefix = if (isDesktop || shouldUseDesktopAgent) baseDesktopAgent else baseAgent
        if (omitVersionComponent) {
            prefix = prefix.replace(AgentRegex.version, "")
        }

        val application = if (!omitApplicationComponent) applicationComponent else null
        return concatWithSpaces(prefix, application, safariComponent)
    }

    private fun containsExcludedPath(url: String?, site: DesktopAgentSiteOnly): Boolean {
        return if (url != null) {
            val segments = url.toUri().pathSegments
            site.excludedPaths.any { segments.contains(it) }
        } else {
            false
        }
    }

    private fun getWebKitVersionOnwards(forDesktop: Boolean): String? {
        val matches = AgentRegex.webkitUntilSafari.find(defaultUserAgent) ?: AgentRegex.webkitUntilEnd.find(defaultUserAgent) ?: return null
        var result = matches.groupValues.last()
        if (forDesktop) {
            result = result.replace(" Mobile", "")
        }
        return result
    }

    private fun concatWithSpaces(vararg elements: String?): String {
        return elements.filterNotNull().joinToString(SPACE)
    }

    private fun getSafariComponent(): String? {
        val matches = AgentRegex.safari.find(defaultUserAgent) ?: return null
        return matches.groupValues.last()
    }

    private object AgentRegex {
        val webkitUntilSafari = Regex("(AppleWebKit/.*) Safari")
        val webkitUntilEnd = Regex("(AppleWebKit/.*)")
        val safari = Regex("(Safari/[^ ]+) *")
        val version = Regex("(Version/[^ ]+) *")
    }

    companion object {
        const val SPACE = " "
        val mobilePrefix = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})"
        val desktopPrefix = "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})"

        val sitesThatOmitApplication = listOf(
            "cvs.com",
            "chase.com",
            "tirerack.com",
            "sovietgames.su",
            "thesun.co.uk",
            "accounts.google.com",
            "mail.google.com"
        )

        val sitesThatOmitVersion = listOf(
            "ing.nl",
            "chase.com",
            "digid.nl",
            "accounts.google.com",
            "xfinity.com"
        )

        val sitesThatShouldUseDesktopAgent = listOf(
            DesktopAgentSiteOnly("m.facebook.com", listOf("dialog", "sharer"))
        )
    }

    data class DesktopAgentSiteOnly(val host: String, val excludedPaths: List<String> = emptyList())
}
