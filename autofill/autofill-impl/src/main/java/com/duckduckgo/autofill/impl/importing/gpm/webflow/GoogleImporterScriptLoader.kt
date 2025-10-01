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

package com.duckduckgo.autofill.impl.importing.gpm.webflow

import com.duckduckgo.autofill.impl.importing.gpm.feature.AutofillImportPasswordConfigStore
import com.duckduckgo.autofill.impl.importing.takeout.store.BookmarkImportConfigStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import javax.inject.Inject

interface GoogleImporterScriptLoader {
    suspend fun getScriptForPasswordImport(): String

    suspend fun getScriptForBookmarkImport(): String
}

@ContributesBinding(FragmentScope::class)
class GoogleImporterScriptLoaderImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val passwordConfigStore: AutofillImportPasswordConfigStore,
    private val bookmarkConfigStore: BookmarkImportConfigStore,
) : GoogleImporterScriptLoader {
    private lateinit var contentScopeJS: String

    override suspend fun getScriptForPasswordImport(): String =
        withContext(dispatchers.io()) {
            getContentScopeJS()
                .replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeScriptJson(loadSettingsJsonPassword()))
                .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, getUnprotectedDomainsJson())
                .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
        }

    override suspend fun getScriptForBookmarkImport(): String =
        withContext(dispatchers.io()) {
            getContentScopeJS()
                .replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeScriptJson(loadSettingsJsonBookmark()))
                .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, getUnprotectedDomainsJson())
                .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
        }

    /**
     * This enables the password import hints feature in C-S-S.
     * These settings are for enabling it; the check for whether it should be enabled or not is done elsewhere.
     */
    private fun getContentScopeScriptJson(settingsJson: String): String =
        """{
            "features":{
                "autofillImport" : {
                    "state": "enabled",
                    "exceptions": [],
                    "settings": $settingsJson
                }
            },
            "unprotectedTemporary":[]
        }

        """.trimMargin()

    private suspend fun loadSettingsJsonPassword(): String = passwordConfigStore.getConfig().javascriptConfigGooglePasswords

    private suspend fun loadSettingsJsonBookmark(): String = bookmarkConfigStore.getConfig().javascriptConfigGoogleTakeout

    private fun getUserPreferencesJson(): String =
        """
            {
                "platform":{
                    "name":"android"
                },
                "messageCallback": '',
                "javascriptInterface": ''
             }
        """.trimMargin()

    private fun getUnprotectedDomainsJson(): String = "[]"

    private fun getContentScopeJS(): String {
        if (!this::contentScopeJS.isInitialized) {
            contentScopeJS = loadJs("autofillImport.js")
        }
        return contentScopeJS
    }

    companion object {
        private const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        private const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        private const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"
    }

    private fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? =
        javaClass.classLoader
            ?.getResource(resourceName)
            ?.openStream()
            ?.bufferedReader()
}
