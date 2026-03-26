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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface AutofillSiteSpecificFixesStore {
    suspend fun getConfig(): AutofillSiteSpecificFixesSettings
}

data class AutofillSiteSpecificFixesSettings(
    val javascriptConfigSiteSpecificFixes: String,
    val canApplySiteSpecificFixes: Boolean,
)

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AutofillSiteSpecificFixesSettingsImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val moshi: Moshi,
) : AutofillSiteSpecificFixesStore {

    private val jsonAdapter: JsonAdapter<JSONObject> by lazy {
        moshi.adapter(JSONObject::class.java)
    }

    override suspend fun getConfig(): AutofillSiteSpecificFixesSettings {
        return withContext(dispatchers.io()) {
            val isSiteSpecificFixesEnabled = autofillFeature.siteSpecificFixes().isEnabled()
            val settings = if (isSiteSpecificFixesEnabled) {
                autofillFeature.siteSpecificFixes().getSettings()?.let {
                    runCatching {
                        jsonAdapter.fromJson(it)
                    }.getOrNull()
                }
            } else {
                null
            }

            val settingsJson = if (isSiteSpecificFixesEnabled && settings != null) {
                settings.toString()
            } else {
                JAVASCRIPT_CONFIG_DEFAULT
            }
            AutofillSiteSpecificFixesSettings(
                javascriptConfigSiteSpecificFixes = settingsJson,
                canApplySiteSpecificFixes = isSiteSpecificFixesEnabled,
            )
        }
    }

    companion object {
        internal const val JAVASCRIPT_CONFIG_DEFAULT = "{}"
    }
}
