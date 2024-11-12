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
import javax.inject.Inject
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface AutofillImportPasswordConfigStore {
    suspend fun getConfig(): AutofillImportPasswordSettings
}

data class AutofillImportPasswordSettings(
    val canImportFromGooglePasswords: Boolean,
    val launchUrlGooglePasswords: String,
    val javascriptConfigGooglePasswords: String,
)

@ContributesBinding(AppScope::class)
class AutofillImportPasswordConfigStoreImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val moshi: Moshi,
) : AutofillImportPasswordConfigStore {

    private val jsonAdapter: JsonAdapter<CanImportFromGooglePasswordManagerConfig> by lazy {
        moshi.adapter(CanImportFromGooglePasswordManagerConfig::class.java)
    }

    override suspend fun getConfig(): AutofillImportPasswordSettings {
        return withContext(dispatchers.io()) {
            val config = autofillFeature.canImportFromGooglePasswordManager().getSettings()?.let { jsonAdapter.fromJson(it) }
            val launchUrl = config?.launchUrl ?: LAUNCH_URL_DEFAULT
            val javascriptConfig = config?.javascriptConfig?.toString() ?: JAVASCRIPT_CONFIG_DEFAULT

            AutofillImportPasswordSettings(
                canImportFromGooglePasswords = autofillFeature.canImportFromGooglePasswordManager().isEnabled(),
                launchUrlGooglePasswords = launchUrl,
                javascriptConfigGooglePasswords = javascriptConfig,
            )
        }
    }

    companion object {
        internal const val JAVASCRIPT_CONFIG_DEFAULT = "\"{}\""
        internal const val LAUNCH_URL_DEFAULT = "https://passwords.google.com/options?ep=1"
    }

    private data class CanImportFromGooglePasswordManagerConfig(
        val launchUrl: String? = null,
        val javascriptConfig: JSONObject? = null,
    )
}
