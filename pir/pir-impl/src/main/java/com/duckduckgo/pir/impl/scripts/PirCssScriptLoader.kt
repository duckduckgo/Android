/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.scripts

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.Locale
import javax.inject.Inject

interface PirCssScriptLoader {
    /**
     * Loads and returns a valid pir script ready to be loaded into a webview.
     */
    suspend fun getScript(): String
}

@ContributesBinding(AppScope::class)
class RealPirCssScriptLoader @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : PirCssScriptLoader {
    private var contentScopeJS: String? = null

    override suspend fun getScript(): String {
        return withContext(dispatcherProvider.io()) {
            if (contentScopeJS.isNullOrBlank()) {
                contentScopeJS = loadJs("brokerProtection.js").run {
                    this.replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeJson())
                        .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, getUnprotectedDomainsJson())
                        .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
                        .replace(MESSAGING_PARAMETERS, "${getSecretKeyValuePair()},${getCallbackKeyValuePair()},${getInterfaceKeyValuePair()}")
                }
            }
            contentScopeJS!!
        }
    }

    private fun getInterfaceKeyValuePair(): String = "\"javascriptInterface\":\"${PIRScriptConstants.SCRIPT_FEATURE_NAME}\""

    private fun getCallbackKeyValuePair(): String = "\"messageCallback\":\"messageCallback\""

    private fun getSecretKeyValuePair(): String = "\"messageSecret\":\"messageSecret\""

    private fun getContentScopeJson(): String {
        // TODO : Get this string from privacy config
        return """{
            "features":{
                "brokerProtection" : {
                    "state": "enabled",
                    "exceptions": [],
                    "settings": {}
                }
            },
            "unprotectedTemporary":[]
        }
        """.trimMargin()
    }

    private fun getUserPreferencesJson(): String {
        val defaultParameters = "${getVersionNumberKeyValuePair()},${getPlatformKeyValuePair()},${getLanguageKeyValuePair()}," +
            "${getSessionKeyValuePair()},${getDesktopModeKeyValuePair()},$MESSAGING_PARAMETERS"
        return "{$defaultParameters}"
    }

    private fun getVersionNumberKeyValuePair() = "\"versionNumber\":${appBuildConfig.versionCode}"
    private fun getPlatformKeyValuePair() = "\"platform\":{\"name\":\"android\"}"
    private fun getLanguageKeyValuePair() = "\"locale\":\"${Locale.getDefault().language}\""
    private fun getDesktopModeKeyValuePair() = "\"desktopModeEnabled\":false"
    private fun getSessionKeyValuePair() = "\"sessionKey\":\"sessionKey\""

    private fun getUnprotectedDomainsJson(): String = "[]"

    private fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }

    companion object {
        private const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        private const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        private const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"
        private const val MESSAGING_PARAMETERS = "\$ANDROID_MESSAGING_PARAMETERS$"
    }
}
