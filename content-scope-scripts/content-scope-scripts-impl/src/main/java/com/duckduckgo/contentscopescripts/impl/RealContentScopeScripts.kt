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

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionManager
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Moshi.Builder
import com.squareup.moshi.Types
import dagger.SingleInstanceIn
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject

interface CoreContentScopeScripts {
    fun getScript(
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ): String

    fun isEnabled(): Boolean

    val secret: String
    val javascriptInterface: String
    val callbackName: String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = CoreContentScopeScripts::class)
@ContributesMultibinding(AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class RealContentScopeScripts @Inject constructor(
    private val pluginPoint: PluginPoint<ContentScopeConfigPlugin>,
    private val userAllowListRepository: UserAllowListRepository,
    private val contentScopeJSReader: ContentScopeJSReader,
    private val appBuildConfig: AppBuildConfig,
    private val unprotectedTemporary: UnprotectedTemporary,
    private val fingerprintProtectionManager: FingerprintProtectionManager,
    private val contentScopeScriptsFeature: ContentScopeScriptsFeature,
) : CoreContentScopeScripts, PrivacyConfigCallbackPlugin {
    // These adapters must be declared before cachedContentScopeJson.
    private val reusableMoshi: Moshi = Builder().build()
    private val userUnprotectedDomainsAdapter: JsonAdapter<List<String>> =
        reusableMoshi.adapter(Types.newParameterizedType(MutableList::class.java, String::class.java))
    private val unprotectedTemporaryAdapter: JsonAdapter<List<FeatureException>> =
        reusableMoshi.adapter(Types.newParameterizedType(MutableList::class.java, FeatureException::class.java))
    private val experimentsAdapter: JsonAdapter<List<Experiment>> =
        reusableMoshi.adapter(Types.newParameterizedType(List::class.java, Experiment::class.java))

    // Plugin config() is driven only by privacy-config persistence, so we cache the assembled
    // config string and refresh it only when a new config is persisted/downloaded, rather than
    // iterating every plugin on every navigation. Set from onPrivacyConfigPersisted() (worker
    // thread) and read in getScript() (main), hence @Volatile.
    @Volatile private var pluginConfigNeedsRebuild: Boolean = true

    // Startup latch: feature repos load their persisted config into memory asynchronously and
    // expose no completion signal, so we keep refreshing the plugin config on every call until it
    // stops changing (repos settled), then trust pluginConfigNeedsRebuild. Only touched from getScript().
    // On a config download the repos reload synchronously inside PrivacyFeaturePlugin.store(), before
    // onPrivacyConfigPersisted() is dispatched, so the latch only has to cover process startup.
    private var pluginConfigSettled: Boolean = false
    private var cachedPluginConfig: String = ""

    private var cachedContentScopeJson: String = getContentScopeJson("", emptyList(), optimizeInjectionEnabled())

    private var cachedUserUnprotectedDomains = CopyOnWriteArrayList<String>()
    private var cachedUserUnprotectedDomainsJson: String = EMPTY_JSON_LIST

    private var cachedUserPreferencesJson: String = EMPTY_JSON

    private var cachedUnprotectTemporaryExceptions = CopyOnWriteArrayList<FeatureException>()
    private var cachedUnprotectTemporaryExceptionsJson: String = EMPTY_JSON_LIST

    private lateinit var cachedContentScopeJS: String

    private var cachedTemplateSegments: List<TemplateSegment>? = null

    override val secret: String = getSecret()
    override val javascriptInterface: String = getSecret()
    override val callbackName: String = getSecret()

    private val cachedMessagingParameters: String =
        "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}"

    private val cachedVersionAndPlatformParameters: String by lazy {
        "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()}"
    }

    override fun getScript(
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ): String {
        return if (optimizeInjectionEnabled()) {
            getOptimizedScript(isDesktopMode, activeExperiments)
        } else {
            getLegacyScript(isDesktopMode, activeExperiments)
        }
    }

    // Original implementation, left intact. Used when optimizeContentScopeInjection is disabled, and
    // deleted together with that flag once the optimized path is fully rolled out.
    private fun getLegacyScript(
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ): String {
        var updateJS = false

        // This path maintains cachedContentScopeJson but not cachedPluginConfig, so the optimized path
        // must re-derive it if the flag is ever flipped back mid-session. Write-only here: nothing below
        // reads it, so legacy behaviour is unaffected.
        pluginConfigNeedsRebuild = true

        val pluginParameters = getPluginParameters()

        if (cachedUnprotectTemporaryExceptions != unprotectedTemporary.unprotectedTemporaryExceptions) {
            cacheUserUnprotectedTemporaryExceptions(unprotectedTemporary.unprotectedTemporaryExceptions, optimized = false)
            updateJS = true
        }

        val contentScopeJson = getContentScopeJson(pluginParameters.config, cachedUnprotectTemporaryExceptions, optimized = false)
        if (cachedContentScopeJson != contentScopeJson) {
            cachedContentScopeJson = contentScopeJson
            updateJS = true
        }

        if (cachedUserUnprotectedDomains != userAllowListRepository.domainsInUserAllowList()) {
            cacheUserUnprotectedDomains(userAllowListRepository.domainsInUserAllowList(), optimized = false)
            updateJS = true
        }

        val userPreferencesJson = getUserPreferencesJson(pluginParameters.preferences, isDesktopMode, activeExperiments, optimized = false)
        if (cachedUserPreferencesJson != userPreferencesJson) {
            cachedUserPreferencesJson = userPreferencesJson
            updateJS = true
        }

        if (!this::cachedContentScopeJS.isInitialized || updateJS) {
            cacheContentScopeJS(optimized = false)
        }
        return cachedContentScopeJS
    }

    // Optimized path: the expensive plugin config() assembly is cached and refreshed only when the
    // privacy config is persisted (plus a startup stabilisation latch, see field docs), and the
    // content scope JSON is re-serialized only when one of its two inputs actually changed. The
    // cheaper per-call inputs (allow list, preferences, cohorts) are unchanged.
    private fun getOptimizedScript(
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ): String {
        var updateJS = false
        var contentScopeChanged = false

        if (pluginConfigNeedsRebuild || !pluginConfigSettled) {
            pluginConfigNeedsRebuild = false
            val pluginConfig = getPluginConfig()
            // An empty config means the repos have not finished loading yet, so never latch on it.
            pluginConfigSettled = pluginConfig.isNotEmpty() && pluginConfig == cachedPluginConfig
            if (pluginConfig != cachedPluginConfig) {
                cachedPluginConfig = pluginConfig
                contentScopeChanged = true
            }
        }

        val unprotectedTemporaryExceptions = unprotectedTemporary.unprotectedTemporaryExceptions
        if (cachedUnprotectTemporaryExceptions != unprotectedTemporaryExceptions) {
            cacheUserUnprotectedTemporaryExceptions(unprotectedTemporaryExceptions, optimized = true)
            contentScopeChanged = true
        }

        if (contentScopeChanged) {
            cachedContentScopeJson = buildContentScopeJson(cachedPluginConfig, cachedUnprotectTemporaryExceptionsJson)
            updateJS = true
        }

        val userUnprotectedDomains = userAllowListRepository.domainsInUserAllowList()
        if (cachedUserUnprotectedDomains != userUnprotectedDomains) {
            cacheUserUnprotectedDomains(userUnprotectedDomains, optimized = true)
            updateJS = true
        }

        val userPreferencesJson = getUserPreferencesJson(getPluginPreferences(), isDesktopMode, activeExperiments, optimized = true)
        if (cachedUserPreferencesJson != userPreferencesJson) {
            cachedUserPreferencesJson = userPreferencesJson
            updateJS = true
        }

        if (!this::cachedContentScopeJS.isInitialized || updateJS) {
            cacheContentScopeJS(optimized = true)
        }
        return cachedContentScopeJS
    }

    override fun isEnabled(): Boolean = contentScopeScriptsFeature.self().isEnabled()

    private fun optimizeInjectionEnabled(): Boolean = contentScopeScriptsFeature.optimizeContentScopeInjection().isEnabled()

    private fun getSecretKeyValuePair() = "\"messageSecret\":\"$secret\""

    private fun getCallbackKeyValuePair() = "\"messageCallback\":\"$callbackName\""

    private fun getInterfaceKeyValuePair() = "\"javascriptInterface\":\"$javascriptInterface\""

    override fun onPrivacyConfigDownloaded() {
        // No-op: invalidation is handled in onPrivacyConfigPersisted(), which fires on every persist
        // (both the startup local-config load and each remote download), whereas this fires on
        // downloads only.
    }

    override fun onPrivacyConfigPersisted() {
        // Invalidate the cached plugin config so the next getScript() rebuilds it. Fires on every
        // config persist (startup local load and each remote download).
        pluginConfigNeedsRebuild = true
    }

    private fun getPluginParameters(): PluginParameters {
        var config = ""
        var preferences = ""
        val plugins = pluginPoint.getPlugins()
        plugins.forEach { plugin ->
            plugin.config().let { pluginConfig ->
                if (pluginConfig.isNotEmpty()) {
                    if (config.isNotEmpty()) {
                        config += ","
                    }
                    config += pluginConfig
                }
            }

            plugin.preferences()?.let { pluginPreferences ->
                if (pluginPreferences.isNotEmpty()) {
                    if (preferences.isNotEmpty()) {
                        preferences += ","
                    }
                    preferences += pluginPreferences
                }
            }
        }
        return PluginParameters(config, preferences)
    }

    private fun getPluginConfig(): String = joinNonEmpty { it.config() }

    private fun getPluginPreferences(): String = joinNonEmpty { it.preferences() }

    private inline fun joinNonEmpty(value: (ContentScopeConfigPlugin) -> String?): String =
        pluginPoint.getPlugins()
            .mapNotNull(value)
            .filter { it.isNotEmpty() }
            .joinToString(",")

    private fun cacheUserUnprotectedDomains(
        userUnprotectedDomains: List<String>,
        optimized: Boolean,
    ) {
        cachedUserUnprotectedDomains.clear()
        if (userUnprotectedDomains.isEmpty()) {
            cachedUserUnprotectedDomainsJson = EMPTY_JSON_LIST
        } else {
            cachedUserUnprotectedDomainsJson = getUserUnprotectedDomainsJson(userUnprotectedDomains, optimized)
            cachedUserUnprotectedDomains.addAll(userUnprotectedDomains)
        }
    }

    private fun cacheUserUnprotectedTemporaryExceptions(
        unprotectedTemporaryExceptions: List<FeatureException>,
        optimized: Boolean,
    ) {
        cachedUnprotectTemporaryExceptions.clear()
        if (unprotectedTemporaryExceptions.isEmpty()) {
            cachedUnprotectTemporaryExceptionsJson = EMPTY_JSON_LIST
        } else {
            cachedUnprotectTemporaryExceptionsJson = getUnprotectedTemporaryJson(unprotectedTemporaryExceptions, optimized)
            cachedUnprotectTemporaryExceptions.addAll(unprotectedTemporaryExceptions)
        }
    }

    private fun cacheContentScopeJS(optimized: Boolean) {
        val contentScopeJS = contentScopeJSReader.getContentScopeJS()

        cachedContentScopeJS = if (optimized) {
            assembleContentScopeJS(contentScopeJS)
        } else {
            val messagingParameters = "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}"
            contentScopeJS
                .replace(CONTENT_SCOPE, cachedContentScopeJson)
                .replace(USER_UNPROTECTED_DOMAINS, cachedUserUnprotectedDomainsJson)
                .replace(USER_PREFERENCES, cachedUserPreferencesJson)
                .replace(MESSAGING_PARAMETERS, messagingParameters)
        }
    }

    private fun assembleContentScopeJS(template: String): String {
        val segments = cachedTemplateSegments ?: splitTemplate(template).also { cachedTemplateSegments = it }
        val builder = StringBuilder(
            template.length +
                cachedContentScopeJson.length +
                cachedUserUnprotectedDomainsJson.length +
                cachedUserPreferencesJson.length +
                cachedMessagingParameters.length,
        )
        segments.forEach { segment ->
            when (segment) {
                is TemplateSegment.Literal -> builder.append(segment.text)
                TemplateSegment.ContentScope -> builder.append(cachedContentScopeJson)
                TemplateSegment.UserUnprotectedDomains -> builder.append(cachedUserUnprotectedDomainsJson)
                TemplateSegment.UserPreferences -> builder.append(cachedUserPreferencesJson)
                TemplateSegment.MessagingParameters -> builder.append(cachedMessagingParameters)
            }
        }
        return builder.toString()
    }

    private fun splitTemplate(template: String): List<TemplateSegment> {
        val tokens = listOf(
            CONTENT_SCOPE to TemplateSegment.ContentScope,
            USER_UNPROTECTED_DOMAINS to TemplateSegment.UserUnprotectedDomains,
            USER_PREFERENCES to TemplateSegment.UserPreferences,
            MESSAGING_PARAMETERS to TemplateSegment.MessagingParameters,
        )
        val segments = mutableListOf<TemplateSegment>()
        var cursor = 0
        while (cursor <= template.length) {
            var matchIndex = -1
            var matchToken = ""
            var matchMarker: TemplateSegment? = null
            for ((token, marker) in tokens) {
                val index = template.indexOf(token, cursor)
                if (index != -1 && (matchIndex == -1 || index < matchIndex)) {
                    matchIndex = index
                    matchToken = token
                    matchMarker = marker
                }
            }
            if (matchMarker == null) {
                if (cursor < template.length) segments.add(TemplateSegment.Literal(template.substring(cursor)))
                break
            }
            if (matchIndex > cursor) segments.add(TemplateSegment.Literal(template.substring(cursor, matchIndex)))
            segments.add(matchMarker)
            cursor = matchIndex + matchToken.length
        }
        return segments
    }

    private fun getUserUnprotectedDomainsJson(
        userUnprotectedDomains: List<String>,
        optimized: Boolean,
    ): String {
        if (optimized) {
            return userUnprotectedDomainsAdapter.toJson(userUnprotectedDomains)
        } else {
            val type = Types.newParameterizedType(MutableList::class.java, String::class.java)
            val moshi = Builder().build()
            val jsonAdapter: JsonAdapter<List<String>> = moshi.adapter(type)
            return jsonAdapter.toJson(userUnprotectedDomains)
        }
    }

    private fun getUnprotectedTemporaryJson(
        unprotectedTemporaryExceptions: List<FeatureException>,
        optimized: Boolean,
    ): String {
        if (optimized) {
            return unprotectedTemporaryAdapter.toJson(unprotectedTemporaryExceptions)
        } else {
            val type = Types.newParameterizedType(MutableList::class.java, FeatureException::class.java)
            val moshi = Builder().build()
            val jsonAdapter: JsonAdapter<List<FeatureException>> = moshi.adapter(type)
            return jsonAdapter.toJson(unprotectedTemporaryExceptions)
        }
    }

    private fun getUserPreferencesJson(
        userPreferences: String,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
        optimized: Boolean,
    ): String {
        val experiments = getExperimentsKeyValuePair(activeExperiments, optimized)
        val messaging = if (optimized) cachedMessagingParameters else MESSAGING_PARAMETERS
        val versionAndPlatform = if (optimized) {
            cachedVersionAndPlatformParameters
        } else {
            "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()}"
        }
        val defaultParameters =
            "$versionAndPlatform,${getLanguageKeyValuePair()}," +
                "${getSessionKeyValuePair()},${getDesktopModeKeyValuePair(isDesktopMode ?: false)},$messaging"
        if (userPreferences.isEmpty()) {
            return "{$experiments,$defaultParameters}"
        }
        return "{$userPreferences,$experiments,$defaultParameters}"
    }

    private fun getVersionNumberKeyValuePair() = "\"versionNumber\":${appBuildConfig.versionCode}"

    private fun getPlatformKeyValuePair() = "\"platform\":{\"name\":\"android\",\"internal\":${appBuildConfig.isInternalBuild()}}"

    private fun getLanguageKeyValuePair() = "\"locale\":\"${Locale.getDefault().language}\""

    private fun getDesktopModeKeyValuePair(isDesktopMode: Boolean) = "\"desktopModeEnabled\":$isDesktopMode"

    private fun getSessionKeyValuePair() = "\"sessionKey\":\"${fingerprintProtectionManager.getSeed()}\""

    // Toggle.getCohort() is suspend but has no suspension points today, so runBlocking runs inline and
    // never parks the caller. If it ever gains real IO this becomes a main-thread stall.
    private fun getExperimentsKeyValuePair(
        activeExperiments: List<Toggle>,
        optimized: Boolean,
    ): String {
        return runBlocking {
            val jsonAdapter: JsonAdapter<List<Experiment>> = if (optimized) {
                experimentsAdapter
            } else {
                val type = Types.newParameterizedType(List::class.java, Experiment::class.java)
                val moshi = Builder().build()
                moshi.adapter(type)
            }
            val experiments = if (optimized) {
                activeExperiments.mapNotNull { toggle ->
                    val cohort = toggle.getCohort() ?: return@mapNotNull null
                    val featureName = toggle.featureName()
                    val parentName = featureName.parentName ?: return@mapNotNull null
                    Experiment(
                        cohort = cohort.name,
                        feature = parentName,
                        subfeature = featureName.name,
                    )
                }
            } else {
                activeExperiments
                    .filter { it.getCohort() != null && it.featureName().parentName != null }
                    .map {
                        Experiment(
                            cohort = it.getCohort()!!.name,
                            feature = it.featureName().parentName!!,
                            subfeature = it.featureName().name,
                        )
                    }
            }
            return@runBlocking "\"currentCohorts\":${jsonAdapter.toJson(experiments)}"
        }
    }

    private fun getContentScopeJson(
        config: String,
        unprotectedTemporaryExceptions: List<FeatureException>,
        optimized: Boolean,
    ): String = buildContentScopeJson(config, getUnprotectedTemporaryJson(unprotectedTemporaryExceptions, optimized))

    private fun buildContentScopeJson(
        config: String,
        unprotectedTemporaryJson: String,
    ): String = "{\"features\":{$config},\"unprotectedTemporary\":$unprotectedTemporaryJson}"

    companion object {
        const val EMPTY_JSON_LIST = "[]"
        const val EMPTY_JSON = "{}"
        const val CONTENT_SCOPE = "\$CONTENT_SCOPE$"
        const val USER_UNPROTECTED_DOMAINS = "\$USER_UNPROTECTED_DOMAINS$"
        const val USER_PREFERENCES = "\$USER_PREFERENCES$"
        const val MESSAGING_PARAMETERS = "\$ANDROID_MESSAGING_PARAMETERS$"

        private fun getSecret(): String = UUID.randomUUID().toString().replace("-", "")
    }
}

data class PluginParameters(
    val config: String,
    val preferences: String,
)

data class Experiment(
    val feature: String,
    val subfeature: String,
    val cohort: String?,
)

private sealed interface TemplateSegment {
    class Literal(val text: String) : TemplateSegment
    object ContentScope : TemplateSegment
    object UserUnprotectedDomains : TemplateSegment
    object UserPreferences : TemplateSegment
    object MessagingParameters : TemplateSegment
}
