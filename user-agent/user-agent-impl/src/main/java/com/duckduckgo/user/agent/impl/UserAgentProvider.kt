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

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import androidx.core.net.toUri
import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.utils.UriString
import com.duckduckgo.common.utils.device.DeviceInfo
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.DefaultPolicy.CLOSEST
import com.duckduckgo.privacy.config.api.DefaultPolicy.DDG
import com.duckduckgo.privacy.config.api.DefaultPolicy.DDG_FIXED
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.duckduckgo.privacy.config.api.UserAgent
import com.duckduckgo.user.agent.api.UserAgentInterceptor
import com.duckduckgo.user.agent.api.UserAgentProvider
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
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
    private val statisticsDataStore: StatisticsDataStore,
    private val clientBrandHintFeature: ClientBrandHintFeature,
) : UserAgentProvider {

    private val baseAgent: String by lazy { concatWithSpaces(mobilePrefix, getWebKitVersionOnwards(false)) }
    private val ddgFixedBaseAgent: String by lazy { concatWithSpaces(ddgFixedMobilePrefix, getWebKitVersionOnwards(false)) }
    private val baseDesktopAgent: String by lazy { concatWithSpaces(desktopPrefix, getWebKitVersionOnwards(true)) }
    private val ddgFixedBaseDesktopAgent: String by lazy { concatWithSpaces(ddgFixedDesktopPrefix, getWebKitVersionOnwards(true)) }
    private val safariComponent: String? by lazy { getSafariComponentFromUserAgent() }
    private val applicationComponent = "DuckDuckGo/${device.majorAppVersion}"

    override fun setHintHeader(settings: WebSettings) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
            val finalBrandList = metadata.brandVersionList.map {
                if (clientBrandHintFeature.self().isEnabled()) {
                    if (it.brand.contains("Android")) {
                        BrandVersion.Builder().setBrand("DuckDuckGo").setFullVersion(it.fullVersion).setMajorVersion(it.majorVersion).build()
                    } else {
                        it
                    }
                } else {
                    if (it.brand.contains("DuckDuckGo")) {
                        BrandVersion.Builder().setBrand("Android WebView").setFullVersion(it.fullVersion).setMajorVersion(it.majorVersion).build()
                    } else {
                        it
                    }
                }
            }
            val ua = UserAgentMetadata.Builder()
                .setPlatform(metadata.platform)
                .setPlatformVersion(metadata.platformVersion)
                .setFullVersion(metadata.fullVersion)
                .setModel(metadata.model)
                .setBitness(metadata.bitness)
                .setArchitecture(metadata.architecture)
                .setWow64(metadata.isWow64)
                .setMobile(metadata.isMobile)
                .setBrandVersionList(finalBrandList)
                .build()
            WebSettingsCompat.setUserAgentMetadata(settings, ua)
        }
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
    override fun userAgent(url: String?, isDesktop: Boolean): String {
        val host = url?.toUri()?.host
        val shouldUseDefaultUserAgent = if (host != null) userAgent.isADefaultException(host) else false

        val isDomainInUserAllowList = isHostInUserAllowedList(host)

        if (isDomainInUserAllowList || !toggle.isFeatureEnabled(PrivacyFeatureName.UserAgentFeatureName.value) || shouldUseDefaultUserAgent) {
            return if (isDesktop) {
                defaultUserAgent.get().replace(AgentRegex.platform, desktopPrefix)
            } else {
                defaultUserAgent.get()
            }
        }

        val shouldUseDdgUserAgent = if (host != null) userAgent.isADdgDefaultSite(host) else false
        if (shouldUseDdgUserAgent) {
            return getDdgUserAgent(url, host, isDesktop)
        }

        val shouldUseDdgFixedUserAgent = if (host != null) userAgent.isADdgFixedSite(host) else false
        if (shouldUseDdgFixedUserAgent) {
            return getDdgFixedUserAgent(url, host, isDesktop)
        }

        statisticsDataStore.atb?.let { atb ->
            val version = atb.version.substringAfter("v").substringBefore("-")
            if (userAgent.isDdgFixedUserAgentVersion(version)) return getDdgFixedUserAgent(url, host, isDesktop)
            if (userAgent.isClosestUserAgentVersion(version)) return getClosestUserAgent(url, host, isDesktop)
        }

        return when (userAgent.defaultPolicy()) {
            DDG -> { getDdgUserAgent(url, host, isDesktop) }
            DDG_FIXED -> { getDdgFixedUserAgent(url, host, isDesktop) }
            CLOSEST -> { getClosestUserAgent(url, host, isDesktop) }
        }
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
        val mobilePrefix = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE})"
        const val ddgFixedMobilePrefix = "Mozilla/5.0 (Linux; Android 10; K)"
        val desktopPrefix = "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})"
        val ddgFixedDesktopPrefix = "Mozilla/5.0 (X11; Linux ${System.getProperty("os.arch")})".replace("aarch64", "x86_64")
        val fallbackDefaultUA = "$mobilePrefix AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/96.0.4664.104 Mobile Safari/537.36"

        val sitesThatShouldUseDesktopAgent = listOf(
            DesktopAgentSiteOnly("m.facebook.com", listOf("dialog", "sharer")),
        )
    }

    data class DesktopAgentSiteOnly(
        val host: String,
        val excludedPaths: List<String> = emptyList(),
    )

    private fun getDdgUserAgent(url: String?, host: String?, isDesktop: Boolean): String {
        val omitApplicationComponent = if (host != null) userAgent.isAnApplicationException(host) else false
        val omitVersionComponent = if (host != null) userAgent.isAVersionException(host) else false
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
        var userAgent = concatWithSpaces(prefix, application, safariComponent)

        userAgentInterceptorPluginPoint.getPlugins().forEach {
            userAgent = it.intercept(userAgent)
        }

        return userAgent
    }

    private fun getDdgFixedUserAgent(url: String?, host: String?, isDesktop: Boolean, isClosest: Boolean = false): String {
        if (!userAgent.ddgFixedUserAgentEnabled() && !isClosest) return getDdgUserAgent(url, host, isDesktop)

        val omitApplicationComponent = if (host != null) userAgent.isAnApplicationException(host) else false
        val shouldUseDesktopAgent =
            if (url != null && host != null) {
                sitesThatShouldUseDesktopAgent.any { UriString.sameOrSubdomain(host, it.host) && !containsExcludedPath(url, it) }
            } else {
                false
            }
        var prefix = if (isDesktop || shouldUseDesktopAgent) ddgFixedBaseDesktopAgent else ddgFixedBaseAgent
        prefix = prefix.replace(AgentRegex.version, "")

        val application = if (!omitApplicationComponent && !isClosest) applicationComponent else null
        var userAgent = concatWithSpaces(prefix, application, safariComponent)

        userAgentInterceptorPluginPoint.getPlugins().forEach {
            userAgent = it.intercept(userAgent)
        }

        return userAgent
    }

    private fun getClosestUserAgent(url: String?, host: String?, isDesktop: Boolean): String {
        if (!userAgent.closestUserAgentEnabled()) return getDdgUserAgent(url, host, isDesktop)
        return getDdgFixedUserAgent(url, host, isDesktop, true)
    }
}

@ContributesTo(AppScope::class)
@Module
class DefaultUserAgentModule {
    @SingleInstanceIn(AppScope::class)
    @Provides
    @Named("defaultUserAgent")
    fun provideDefaultUserAgent(context: Context): String {
        return runCatching {
            WebSettings.getDefaultUserAgent(context)
        }.getOrDefault(RealUserAgentProvider.fallbackDefaultUA)
    }
}
