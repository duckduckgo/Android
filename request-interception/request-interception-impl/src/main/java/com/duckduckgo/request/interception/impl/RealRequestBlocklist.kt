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

package com.duckduckgo.request.interception.impl

import android.net.Uri
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.request.interception.api.RequestBlocklist
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, RequestBlocklist::class)
@ContributesMultibinding(AppScope::class, PrivacyConfigCallbackPlugin::class)
class RealRequestBlocklist @Inject constructor(
    private val requestBlocklistFeature: RequestBlocklistFeature,
    private val dispatchers: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    moshi: Moshi,
) : RequestBlocklist, PrivacyConfigCallbackPlugin {

    private val blockedRequests = ConcurrentHashMap<String, List<BlocklistRuleEntity>>()

    @Volatile
    private var exceptions = listOf<Domain>()

    private val blockListSettingsJsonAdapter: JsonAdapter<RequestBlocklistSettings> =
        moshi.adapter(RequestBlocklistSettings::class.java)

    init {
        if (isMainProcess) {
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    override fun containedInBlocklist(
        documentUrl: Uri,
        requestUrl: Uri,
    ): Boolean {
        if (!requestBlocklistFeature.self().isEnabled()) {
            return false
        }

        val documentHost = documentUrl.baseHost.orEmpty()

        if (isAnException(documentHost)) return false

        val httpUrl = requestUrl.toString().toHttpUrlOrNull() ?: return false
        val requestDomain = httpUrl.topPrivateDomain() ?: return false

        val rules = blockedRequests[requestDomain] ?: return false

        val normalizedUrl = httpUrl.toString()

        return rules.any { rule ->
            rule.rule.containsMatchIn(normalizedUrl) && domainMatches(documentHost, rule)
        }
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatchers.io()) {
            val newBlockedRequests = ConcurrentHashMap<String, List<BlocklistRuleEntity>>()

            requestBlocklistFeature.self().getSettings()?.let { settingsJson ->
                runCatching {
                    val settings = blockListSettingsJsonAdapter.fromJson(settingsJson)

                    settings?.blockedRequests?.entries?.forEach { entry ->
                        val domain = entry.key
                        val topPrivateDomain = "https://$domain".toHttpUrlOrNull()?.topPrivateDomain()
                        if (topPrivateDomain != null && topPrivateDomain == domain) {
                            val validRules = entry.value.rules?.mapNotNull { BlocklistRuleEntity.fromJson(it) } ?: emptyList()
                            if (validRules.isNotEmpty()) {
                                newBlockedRequests[domain] = validRules
                            }
                        }
                    }
                }.onFailure {
                    logcat { "RequestBlocklist: Failed to parse settings: ${it.message}" }
                }
            }

            blockedRequests.clear()
            blockedRequests.putAll(newBlockedRequests)
            exceptions = requestBlocklistFeature.self().getExceptions().map { Domain(it.domain) }
        }
    }

    private fun isAnException(documentHost: String): Boolean {
        return exceptions.any { exception ->
            UriString.sameOrSubdomain(documentHost.toDomain(), exception)
        }
    }

    private fun domainMatches(
        documentHost: String,
        rule: BlocklistRuleEntity,
    ): Boolean {
        if (rule.applyToAllDomains) return true
        return rule.domains.any { domain -> UriString.sameOrSubdomain(documentHost.toDomain(), domain) }
    }

    private fun String.toDomain() = Domain(this)
}
