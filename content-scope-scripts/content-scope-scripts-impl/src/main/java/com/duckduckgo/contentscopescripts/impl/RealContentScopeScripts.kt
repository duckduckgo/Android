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
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.Gpc
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
    private val gpc: Gpc,
    private val contentScopeJS: ContentScopeJS
) : ContentScopeScripts {

    private var cachedConfig: String = ""
    private lateinit var cachedContentScopeJson: String

    private var cachedUserUnprotectedDomains = CopyOnWriteArrayList<String>()
    private var cachedUserUnprotectedDomainsJson: String = emptyJsonList

    private var cachedUserPreferences = getUserPreferences()
    private lateinit var cachedUserPreferencesJson: String

    private lateinit var cachedContentScopeJS: String

    override fun getScript(): String {
        var updateJS = false

        val config = getConfig()
        if (!this::cachedContentScopeJson.isInitialized || cachedConfig != config) {
            cacheContentScope(config)
            updateJS = true
        }

        if (cachedUserUnprotectedDomains != allowList.userWhiteList) {
            cacheUserUnprotectedDomains(allowList.userWhiteList)
            updateJS = true
        }

        if (!this::cachedUserPreferencesJson.isInitialized || cachedUserPreferences.globalPrivacyControlValue != gpc.isEnabled()) {
            cacheUserPreferences(getUserPreferences())
            updateJS = true
        }

        if (!this::cachedContentScopeJS.isInitialized || updateJS) {
            cacheContentScopeJS()
        }

        return cachedContentScopeJS
    }

    private fun getConfig(): String {
        var config = ""
        for (plugin in pluginPoint.getPlugins()) {
            plugin.config()?.let { pluginConfig ->
                config += pluginConfig
                config += ",\n"
            }
        }
        return config
    }

    private fun cacheContentScope(config: String) {
        cachedConfig = config
        cachedContentScopeJson = getContentScopeJson(config)
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

    private fun cacheUserPreferences(userPreferences: UserPreferences) {
        cachedUserPreferences = userPreferences
        cachedUserPreferencesJson = getUserPreferencesJson(userPreferences)
    }

    private fun cacheContentScopeJS() {
        val contentScopeJS = contentScopeJS.getContentScopeJS()

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

    private fun getUserPreferencesJson(userPreferences: UserPreferences): String {
        val moshi = Builder().build()
        val jsonAdapter: JsonAdapter<UserPreferences> = moshi.adapter(UserPreferences::class.java)
        return jsonAdapter.toJson(userPreferences)
    }

    private fun getContentScopeJson(config: String): String = (
        "{\"features\":{$config\"unprotectedTemporary\":[]}}"
        )

    private fun getUserPreferences() = UserPreferences(
        globalPrivacyControlValue = gpc.isEnabled()
    )

    companion object {
        const val emptyJsonList = "[]"
        const val contentScope = "\$CONTENT_SCOPE$"
        const val userUnprotectedDomains = "\$USER_UNPROTECTED_DOMAINS$"
        const val userPreferences = "\$USER_PREFERENCES$"
    }
}
