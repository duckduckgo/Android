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

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.userwhitelist.api.UserWhiteListRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.duckduckgo.privacy.config.api.UnprotectedTemporaryException
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealContentScopeScripts @Inject constructor(
    private val pluginPoint: PluginPoint<ContentScopeConfigPlugin>,
    private val allowList: UserWhiteListRepository,
    private val contentScopeJSReader: ContentScopeJSReader,
    private val appBuildConfig: AppBuildConfig,
    private val unprotectedTemporary: UnprotectedTemporary
) : ContentScopeScripts {

    private var cachedContentScopeJson: String = getContentScopeJson("", emptyList())

    private var cachedUserUnprotectedDomains = CopyOnWriteArrayList<String>()
    private var cachedUserUnprotectedDomainsJson: String = emptyJsonList

    private var cachedUserPreferencesJson: String = emptyJson

    private var cachedUnprotectTemporaryExceptions = CopyOnWriteArrayList<UnprotectedTemporaryException>()
    private var cachedUnprotectTemporaryExceptionsJson: String = emptyJsonList

    private lateinit var cachedContentScopeJS: String

    override fun getScript(): String {
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

        if (cachedUserUnprotectedDomains != allowList.userWhiteList) {
            cacheUserUnprotectedDomains(allowList.userWhiteList)
            updateJS = true
        }

        val userPreferencesJson = getUserPreferencesJson(pluginParameters.preferences)
        if (cachedUserPreferencesJson != userPreferencesJson) {
            cachedUserPreferencesJson = userPreferencesJson
            updateJS = true
        }

        if (!this::cachedContentScopeJS.isInitialized || updateJS) {
            cacheContentScopeJS()
        }
        return cachedContentScopeJS
    }

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

    private fun cacheUserUnprotectedTemporaryExceptions(unprotectedTemporaryExceptions: List<UnprotectedTemporaryException>) {
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
    }

    private fun getUserUnprotectedDomainsJson(userUnprotectedDomains: List<String>): String {
        val type = Types.newParameterizedType(MutableList::class.java, String::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<String>> = moshi.adapter(type)
        return jsonAdapter.toJson(userUnprotectedDomains)
    }

    private fun getUnprotectedTemporaryJson(unprotectedTemporaryExceptions: List<UnprotectedTemporaryException>): String {
        val type = Types.newParameterizedType(MutableList::class.java, UnprotectedTemporaryException::class.java)
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<List<UnprotectedTemporaryException>> = moshi.adapter(type)
        return jsonAdapter.toJson(unprotectedTemporaryExceptions)
    }

    private fun getUserPreferencesJson(userPreferences: String): String {
        val defaultParameters = "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()}"
        if (userPreferences.isEmpty()) {
            return "{$defaultParameters}"
        }
        return "{$userPreferences,$defaultParameters}"
    }

    private fun getVersionNumberKeyValuePair() = "\"versionNumber\":${appBuildConfig.versionCode}"

    private fun getPlatformKeyValuePair() = "\"platform\":{\"name\":\"android\"}"

    private fun getContentScopeJson(config: String, unprotectedTemporaryExceptions: List<UnprotectedTemporaryException>): String = (
        "{\"features\":{$config},\"unprotectedTemporary\":${getUnprotectedTemporaryJson(unprotectedTemporaryExceptions)}}"
        )

    companion object {
        const val emptyJsonList = "[]"
        const val emptyJson = "{}"
        const val contentScope = "\$CONTENT_SCOPE$"
        const val userUnprotectedDomains = "\$USER_UNPROTECTED_DOMAINS$"
        const val userPreferences = "\$USER_PREFERENCES$"
    }
}

data class PluginParameters(
    val config: String,
    val preferences: String
)
