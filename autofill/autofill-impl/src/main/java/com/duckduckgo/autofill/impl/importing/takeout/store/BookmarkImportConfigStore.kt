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

package com.duckduckgo.autofill.impl.importing.takeout.store

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

interface BookmarkImportConfigStore {
    suspend fun getConfig(): BookmarkImportSettings
}

data class BookmarkImportSettings(
    val canImportFromGoogleTakeout: Boolean,
    val launchUrlGoogleTakeout: String,
    val canInjectJavascript: Boolean,
    val javascriptConfigGoogleTakeout: String,
)

@ContributesBinding(AppScope::class)
class BookmarkImportConfigStoreImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
    private val moshi: Moshi,
) : BookmarkImportConfigStore {
    private val jsonAdapter: JsonAdapter<ImportConfigJson> by lazy {
        moshi.adapter(ImportConfigJson::class.java)
    }

    override suspend fun getConfig(): BookmarkImportSettings =
        withContext(dispatchers.io()) {
            val config =
                autofillFeature.canImportBookmarksFromGoogleTakeout().getSettings()?.let {
                    runCatching {
                        jsonAdapter.fromJson(it)
                    }.getOrNull()
                }

            BookmarkImportSettings(
                canImportFromGoogleTakeout = autofillFeature.canImportBookmarksFromGoogleTakeout().isEnabled(),
                launchUrlGoogleTakeout = config?.launchUrl ?: LAUNCH_URL_DEFAULT,
                canInjectJavascript = config?.canInjectJavascript ?: CAN_INJECT_JAVASCRIPT_DEFAULT,
                javascriptConfigGoogleTakeout = config?.javascriptConfig?.toString() ?: JAVASCRIPT_CONFIG_DEFAULT,
            )
        }

    companion object {
        internal const val JAVASCRIPT_CONFIG_DEFAULT = "\"{}\""
        internal const val CAN_INJECT_JAVASCRIPT_DEFAULT = true

        internal const val LAUNCH_URL_DEFAULT = "https://takeout.google.com/settings/takeout"
    }

    private data class ImportConfigJson(
        val launchUrl: String? = null,
        val canInjectJavascript: Boolean = CAN_INJECT_JAVASCRIPT_DEFAULT,
        val javascriptConfig: JSONObject? = null,
    )
}
