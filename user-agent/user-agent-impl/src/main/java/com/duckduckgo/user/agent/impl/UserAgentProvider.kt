/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.user.agent.impl

import android.os.Build
import androidx.core.net.toUri
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.user.agent.api.UserAgentInterceptor
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.duckduckgo.user.agent.store.UserAgentFeatureName
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

/**
 * Example Default User Agent (From Chrome):
 * Mozilla/5.0 (Linux; Android 8.1.0; Nexus 6P Build/OPM3.171019.014) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Mobile Safari/537.36
 *
 * Example Default Desktop User Agent (From Chrome):
 * Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/64.0.3282.137 Safari/537.36
 */
@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealUserAgentProvider @Inject constructor(
    @Named("defaultUserAgent") private val defaultUserAgent: Provider<String>,
    device: DeviceInfo,
    private val userAgentInterceptorPluginPoint: PluginPoint<UserAgentInterceptor>,
    private val userAgent: UserAgent,
    private val toggle: FeatureToggle,
    private val userAllowListRepository: UserAllowListRepository,
) : UserAgentProvider {

    private val baseAgent: String by lazy { concatWithSpaces(mobilePrefix, getWebKitVersionOnwards(false)) }
    private val fallbackBaseAgent: String by lazy { concatWithSpaces(fallbackMobilePrefix, getWebKitVersionOnwards(false)) }

    private val baseDesktopAgent: String by lazy { concatWithSpaces(desktopPrefix, getWebKitVersionOnwards(true)) }
    private val fallbackBaseDesktopAgent: String by lazy { concatWithSpaces(fallbackDesktopPrefix, getWebKitVersionOnwards(true)) }

    private val safariComponent: String? by lazy { getSafariComponentFromUserAgent() }
    private val applicationComponent = "DuckDuckGo/${device.majorAppVersion}"

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
    override fun userAgent(url: String?, isDesktop: Boolean): String {
        val host = url?.toUri()?.host
        val shouldUseDefaultUserAgent = if (host != null) userAgent.isException(host) else false

        val isDomainInUserAllowList = isHostInUserAllowedList(host)

        if (isDomainInUserAllowList || !toggle.isFeatureEnabled(UserAgentFeatureName.UserAgent.value) || shouldUseDefaultUserAgent) {
            return if (isDesktop) {
                defaultUserAgent.get().replace(AgentRegex.platform, fallbackDesktopPrefix)
            } else {
                defaultUserAgent.get()
            }
        }

        val shouldUseLegacyUserAgent = if (host != null) userAgent.useLegacyUserAgent(host) else false
        if (shouldUseLegacyUserAgent) {
            return getUserAgent(url = url, host = host, isDesktop = isDesktop, useLegacy = true)
        }

        return getUserAgent(url = url, host = host, isDesktop = isDesktop)
    }

    private fun isHostInUserAllowedList(host: String?): Boolean {
        return if (host == null) {
            false
        } else {
            userAllowListRepository.isDomainInUserAllowList(host)
        }
    }

    private fun containsExcludedPath(
        url: String?,
        site: DesktopAgentSiteOnly,
    ): Boolean {
        return if (url != null) {
            val segments = url.toUri().pathSegments
            site.excludedPaths.any { segments.contains(it) }
        } else {
            false
        }
    }

    private fun getWebKitVersionOnwards(forDesktop: Boolean): String? {
        val matches =
            AgentRegex.webkitUntilSafari.find(defaultUserAgent.get()) ?: AgentRegex.webkitUntilEnd.find(defaultUserAgent.get()) ?: return null
        var result = matches.groupValues.last()
        if (forDesktop) {
            result = result.replace(" Mobile", "")
        }
        return result.replace(AgentRegex.chrome, "$1.0.0.0")
    }

    private fun concatWithSpaces(vararg elements: String?): String {
        return elements.filterNotNull().joinToString(SPACE)
    }

    private fun getSafariComponentFromUserAgent(): String? {
        val matches = AgentRegex.safari.find(defaultUserAgent.get()) ?: return null
        return matches.groupValues.last()
    }

    private object AgentRegex {
        val webkitUntilSafari = Regex("(AppleWebKit/.*) Safari")
        val webkitUntilEnd = Regex("(AppleWebKit/.*)")
        val safari = Regex("(Safari/[^ ]+) *")
        val version = Regex("(Version/[^ ]+) *")
        val platform = Regex(".*Linux; Android \\d+")
        val chrome = Regex("(Chrome/\\d+)\\.\\d+\\.\\d+\\.\\d+")
    }

    companion object {
        const val SPACE = " "

        const val mobilePrefix = "Mozilla/5.0 (Linux; Android 10; K)"
        val fallbackMobilePrefix = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})"

        val desktopPrefix = "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})".replace("aarch64", "x86_64")
        val fallbackDesktopPrefix = "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})"

        val fallbackDefaultUA = "$fallbackMobilePrefix AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/96.0.4664.104 Mobile Safari/537.36"

        val sitesThatShouldUseDesktopAgent = listOf(
            DesktopAgentSiteOnly("m.facebook.com", listOf("dialog", "sharer")),
        )
    }

    data class DesktopAgentSiteOnly(
        val host: String,
        val excludedPaths: List<String> = emptyList(),
    )

    private fun getUserAgent(url: String?, host: String?, isDesktop: Boolean, useLegacy: Boolean = false): String {
        val shouldUseDesktopAgent = if (url != null && host != null) {
            sitesThatShouldUseDesktopAgent.any { UriString.sameOrSubdomain(host, it.host) && !containsExcludedPath(url, it) }
        } else {
            false
        }

        val prefix = when {
            useLegacy -> if (isDesktop || shouldUseDesktopAgent) fallbackBaseDesktopAgent else fallbackBaseAgent
            else -> if (isDesktop || shouldUseDesktopAgent) baseDesktopAgent else baseAgent
        }.let { if (useLegacy) it else it.replace(AgentRegex.version, "") }

        var userAgent = if (useLegacy) {
            concatWithSpaces(prefix, applicationComponent, safariComponent)
        } else {
            concatWithSpaces(prefix, null, safariComponent)
        }

        userAgentInterceptorPluginPoint.getPlugins().forEach {
            userAgent = it.intercept(userAgent)
        }

        return userAgent
    }
}
