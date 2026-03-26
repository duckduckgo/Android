/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.gpm.feature

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface AutofillImportPasswordConfigStore {
    suspend fun getConfig(): AutofillImportPasswordSettings
}

data class AutofillImportPasswordSettings(
    val canImportFromGooglePasswords: Boolean,
    val launchUrlGooglePasswords: String,
    val canInjectJavascript: Boolean,
    val javascriptConfigGooglePasswords: String,
    val urlMappings: List<UrlMapping>,
)

data class UrlMapping(
    val key: String,
    val url: String,
)

@ContributesBinding(AppScope::class)
class AutofillImportPasswordConfigStoreImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val moshi: Moshi,
) : AutofillImportPasswordConfigStore {

    private val jsonAdapter: JsonAdapter<ImportConfigJson> by lazy {
        moshi.adapter(ImportConfigJson::class.java)
    }

    override suspend fun getConfig(): AutofillImportPasswordSettings {
        return withContext(dispatchers.io()) {
            val config = autofillFeature.canImportFromGooglePasswordManager().getSettings()?.let {
                runCatching {
                    jsonAdapter.fromJson(it)
                }.getOrNull()
            }

            AutofillImportPasswordSettings(
                canImportFromGooglePasswords = autofillFeature.canImportFromGooglePasswordManager().isEnabled(),
                launchUrlGooglePasswords = config?.launchUrl ?: LAUNCH_URL_DEFAULT,
                canInjectJavascript = config?.canInjectJavascript ?: CAN_INJECT_JAVASCRIPT_DEFAULT,
                javascriptConfigGooglePasswords = config?.javascriptConfig?.toString() ?: JAVASCRIPT_CONFIG_DEFAULT,
                urlMappings = config?.urlMappings.convertFromJsonModel(),
            )
        }
    }

    companion object {
        internal const val JAVASCRIPT_CONFIG_DEFAULT = "\"{}\""
        internal const val CAN_INJECT_JAVASCRIPT_DEFAULT = true

        internal const val LAUNCH_URL_DEFAULT = "https://passwords.google.com/options?ep=1"

        // order is important; first match wins so keep the most specific to start of the list
        internal val URL_MAPPINGS_DEFAULT = listOf(
            UrlMapping(key = "webflow-signin-rejected", url = "https://accounts.google.com/v3/signin/rejected"),
            UrlMapping(key = "webflow-passphrase-encryption", url = "https://passwords.google.com/error/sync-passphrase"),
            UrlMapping(key = "webflow-pre-login", url = "https://passwords.google.com/intro"),
            UrlMapping(key = "webflow-export", url = "https://passwords.google.com/options?ep=1"),
            UrlMapping(key = "webflow-authenticate", url = "https://accounts.google.com/"),
            UrlMapping(key = "webflow-post-login-landing", url = "https://passwords.google.com"),
        )
    }

    private data class ImportConfigJson(
        val launchUrl: String? = null,
        val canInjectJavascript: Boolean = CAN_INJECT_JAVASCRIPT_DEFAULT,
        val javascriptConfig: JSONObject? = null,
        val urlMappings: List<UrlMappingJson>? = null,
    )

    private data class UrlMappingJson(
        val key: String,
        val url: String,
    )

    private fun List<UrlMappingJson>?.convertFromJsonModel(): List<UrlMapping> {
        return this?.let { jsonList ->
            jsonList.map { UrlMapping(key = it.key, url = it.url) }
        } ?: URL_MAPPINGS_DEFAULT
    }
}
