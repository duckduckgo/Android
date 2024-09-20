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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.autofill.impl.di.AutofillModule.AutofillImportPasswordsFeatureDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface AutofillImportPasswordsFeatureStore {
    suspend fun updateAutofillImportPasswordsSettings(json: String)
    suspend fun getAutofillImportPasswordsSettings(): String
}

@ContributesBinding(AppScope::class, boundType = AutofillImportPasswordsFeatureStore::class)
@SingleInstanceIn(AppScope::class)
class AutofillImportPasswordsFeatureStoreImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    @AutofillImportPasswordsFeatureDataStore private val dataStore: DataStore<Preferences>,
) : AutofillImportPasswordsFeatureStore {

    override suspend fun updateAutofillImportPasswordsSettings(json: String) {
        withContext(dispatchers.io()) {
            dataStore.edit {
                it[settingsKey] = json
            }
        }
    }
    override suspend fun getAutofillImportPasswordsSettings(): String {
        return withContext(dispatchers.io()) {
            dataStore.data.map {
                it[settingsKey] ?: DEFAULT_SETTINGS
            }.firstOrNull() ?: DEFAULT_SETTINGS
        }
    }

    companion object {
        val settingsKey = stringPreferencesKey("settings")
        // private const val DEFAULT_SETTINGS = "{}"

        // this is temporary until the remote config is finalized and live. Then can revert this to the commented out line above.
        private const val DEFAULT_SETTINGS = """
            {
                "settingsButton": {
                    "shouldAutotap": false,
                    "selectors": ["a[href*='options']"],
                    "labelTexts": ['Password options']
                },
                "exportButton": {
                    "shouldAutotap": false,
                    "selectors": ["c-wiz[data-node-index*='2;0'][data-p*='options']", "c-wiz[data-p*='options'][jsdata='deferred-i4']"],
                    "labelTexts": ['Export']
                },
                "signInButton": {
                     "shouldAutotap": false,
                     "selectors": ['a[href*="ServiceLogin"]:not([target="_top"])', 'a[aria-label="Sign in"]:not([target="_top"])'],
                     "labelTexts": ['Sign in']
                }
            }
        """
    }
}
