/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionManager
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

interface CoreContentScopeScripts {
    fun getScript(site: Site?): String
    fun isEnabled(): Boolean

    val secret: String
    val javascriptInterface: String
    val callbackName: String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealContentScopeScripts @Inject constructor(
    private val pluginPoint: PluginPoint<ContentScopeConfigPlugin>,
    private val userAllowListRepository: UserAllowListRepository,
    private val contentScopeJSReader: ContentScopeJSReader,
    private val appBuildConfig: AppBuildConfig,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val fingerprintProtectionManager: FingerprintProtectionManager,
    private val contentScopeScriptsFeature: ContentScopeScriptsFeature,
) : CoreContentScopeScripts {

    private var cachedContentScopeJson: String = getContentScopeJson("", emptyList())

    private var cachedUserUnprotectedDomains = CopyOnWriteArrayList<String>()
    private var cachedUserUnprotectedDomainsJson: String = emptyJsonList

    private var cachedUserPreferencesJson: String = emptyJson

    private var cachedUnprotectTemporaryExceptions = CopyOnWriteArrayList<FeatureException>()
    private var cachedUnprotectTemporaryExceptionsJson: String = emptyJsonList

    private lateinit var cachedContentScopeJS: String

    override val secret: String = getSecret()
    override val javascriptInterface: String = getSecret()
    override val callbackName: String = getSecret()

    override fun getScript(site: Site?): String {
        var updateJS = false

        val pluginParameters = getPluginParameters()

        if (cachedUnprotectTemporaryExceptions != unprotectedTemporary.unprotectedTemporaryExceptions) {
            cacheUserUnprotectedTemporaryExceptions(unprotectedTemporary.unprotectedTemporaryExceptions)
            updateJS = true
        }

        val contentScopeJson = getContentScopeJson(pluginParameters.config, cachedUnprotectTemporaryExceptions)
        if (cachedContentScopeJson != contentScopeJson) {
            cachedContentScopeJson = contentScopeJson
            updateJS = true
        }

        if (cachedUserUnprotectedDomains != userAllowListRepository.domainsInUserAllowList()) {
            cacheUserUnprotectedDomains(userAllowListRepository.domainsInUserAllowList())
            updateJS = true
        }

        val userPreferencesJson = getUserPreferencesJson(pluginParameters.preferences, site)
        if (cachedUserPreferencesJson != userPreferencesJson) {
            cachedUserPreferencesJson = userPreferencesJson
            updateJS = true
        }

        if (!this::cachedContentScopeJS.isInitialized || updateJS) {
            cacheContentScopeJS()
        }
        return cachedContentScopeJS
    }

    override fun isEnabled(): Boolean {
        return contentScopeScriptsFeature.self().isEnabled()
    }

    private fun getSecretKeyValuePair() = "\"messageSecret\":\"$secret\""
    private fun getCallbackKeyValuePair() = "\"messageCallback\":\"$callbackName\""
    private fun getInterfaceKeyValuePair() = "\"javascriptInterface\":\"$javascriptInterface\""

    private fun getPluginParameters(): PluginParameters {
        var config = ""
        var preferences = ""
        val plugins = pluginPoint.getPlugins()
        plugins.forEach { plugin ->
            if (config.isNotEmpty()) {
                config += ","
            }
            config += plugin.config()

            plugin.preferences()?.let { pluginPreferences ->
                if (preferences.isNotEmpty()) {
                    preferences += ","
                }
                preferences += pluginPreferences
            }
        }
        return PluginParameters(config, preferences)
    }

    private fun cacheUserUnprotectedDomains(userUnprotectedDomains: List<String>) {
        cachedUserUnprotectedDomains.clear()
        if (userUnprotectedDomains.isEmpty()) {
            cachedUserUnprotectedDomainsJson = emptyJsonList
        } else {
            cachedUserUnprotectedDomainsJson = getUserUnprotectedDomainsJson(userUnprotectedDomains)
            cachedUserUnprotectedDomains.addAll(userUnprotectedDomains)
        }
    }

    private fun cacheUserUnprotectedTemporaryExceptions(unprotectedTemporaryExceptions: List<FeatureException>) {
        cachedUnprotectTemporaryExceptions.clear()
        if (unprotectedTemporaryExceptions.isEmpty()) {
            cachedUnprotectTemporaryExceptionsJson = emptyJsonList
        } else {
            cachedUnprotectTemporaryExceptionsJson = getUnprotectedTemporaryJson(unprotectedTemporaryExceptions)
            cachedUnprotectTemporaryExceptions.addAll(unprotectedTemporaryExceptions)
        }
    }

    private fun cacheContentScopeJS() {
        val contentScopeJS = contentScopeJSReader.getContentScopeJS()

        cachedContentScopeJS = contentScopeJS
            .replace(contentScope, cachedContentScopeJson)
            .replace(userUnprotectedDomains, cachedUserUnprotectedDomainsJson)
            .replace(userPreferences, cachedUserPreferencesJson)
            .replace(messagingParameters, "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}")
    }

    private fun getUserUnprotectedDomainsJson(userUnprotectedDomains: List<String>): String {
        val type = Types.newParameterizedType(MutableList::class.java, String::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<String>> = moshi.adapter(type)
        return jsonAdapter.toJson(userUnprotectedDomains)
    }

    private fun getUnprotectedTemporaryJson(unprotectedTemporaryExceptions: List<FeatureException>): String {
        val type = Types.newParameterizedType(MutableList::class.java, FeatureException::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<FeatureException>> = moshi.adapter(type)
        return jsonAdapter.toJson(unprotectedTemporaryExceptions)
    }

    private fun getUserPreferencesJson(userPreferences: String, site: Site?): String {
        val isDesktopMode = site?.isDesktopMode ?: false
        val defaultParameters = "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()},${getLanguageKeyValuePair()}," +
            "${getSessionKeyValuePair()},${getDesktopModeKeyValuePair(isDesktopMode)},$messagingParameters"
        if (userPreferences.isEmpty()) {
            return "{$defaultParameters}"
        }
        return "{$userPreferences,$defaultParameters}"
    }

    private fun getVersionNumberKeyValuePair() = "\"versionNumber\":${appBuildConfig.versionCode}"
    private fun getPlatformKeyValuePair() = "\"platform\":{\"name\":\"android\"}"
    private fun getLanguageKeyValuePair() = "\"locale\":\"${Locale.getDefault().language}\""
    private fun getDesktopModeKeyValuePair(isDesktopMode: Boolean) = "\"desktopModeEnabled\":$isDesktopMode"
    private fun getSessionKeyValuePair() = "\"sessionKey\":\"${fingerprintProtectionManager.getSeed()}\""

    private fun getContentScopeJson(config: String, unprotectedTemporaryExceptions: List<FeatureException>): String = (
        "{\"features\":{$config},\"unprotectedTemporary\":${getUnprotectedTemporaryJson(unprotectedTemporaryExceptions)}}"
        )

    companion object {
        const val emptyJsonList = "[]"
        const val emptyJson = "{}"
        const val contentScope = "\$CONTENT_SCOPE$"
        const val userUnprotectedDomains = "\$USER_UNPROTECTED_DOMAINS$"
        const val userPreferences = "\$USER_PREFERENCES$"
        const val messagingParameters = "\$ANDROID_MESSAGING_PARAMETERS$"

        private fun getSecret(): String {
            return UUID.randomUUID().toString().replace("-", "")
        }
    }
}

data class PluginParameters(
    val config: String,
    val preferences: String,
)
