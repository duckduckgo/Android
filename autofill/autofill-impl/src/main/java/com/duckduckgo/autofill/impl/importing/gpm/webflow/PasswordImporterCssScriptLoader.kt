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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.BufferedReader
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface PasswordImporterScriptLoader {
    suspend fun getScript(): String
}

@ContributesBinding(FragmentScope::class)
class PasswordImporterCssScriptLoader @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PasswordImporterScriptLoader {

    private lateinit var contentScopeJS: String

    override suspend fun getScript(): String {
        return withContext(dispatchers.io()) {
            getContentScopeJS()
                .replace(CONTENT_SCOPE_PLACEHOLDER, getContentScopeJson())
                .replace(USER_UNPROTECTED_DOMAINS_PLACEHOLDER, getUnprotectedDomainsJson())
                .replace(USER_PREFERENCES_PLACEHOLDER, getUserPreferencesJson())
        }
    }

    private fun getContentScopeJson(
        showHintSignInButton: Boolean = true,
        showHintSettingsButton: Boolean = true,
        showHintExportButton: Boolean = true,
    ): String = (
        """{
            "features":{
                "passwordImport" : {
                    "state": "enabled",
                    "exceptions": [],
                    "settings": {
                        "settingsButton": {
                            "highlight": {
                                "enabled": $showHintSettingsButton,
                                "selector": "bla bla"
                            },
                            "autotap": {
                                "enabled": false,
                                "selector": "bla bla"
                            }
                        },
                        "exportButton": {
                            "highlight": {
                                "enabled": $showHintExportButton,
                                "selector": "bla bla"
                            },
                            "autotap": {
                                "enabled": false,
                                "selector": "bla bla"
                            }
                        },
                        "signInButton": {
                             "highlight":{
                                "enabled": $showHintSignInButton,
                                "selector": "bla bla"
                            },
                            "autotap": {
                                "enabled": false,
                                "selector": "bla bla"
                            }
                        }
                    }
                }
            },
            "unprotectedTemporary":[]
        }
            
        """.trimMargin()
        )

    private fun getUserPreferencesJson(): String {
        return """
            {
                "platform":{
                    "name":"android"
                },
                "messageCallback": '',
                "javascriptInterface": ''
             }
        """.trimMargin()
    }

    private fun getUnprotectedDomainsJson(): String = "[]"

    private fun getContentScopeJS(): String {
        if (!this::contentScopeJS.isInitialized) {
            contentScopeJS = loadJs("passwordImport.js")
        }
        return contentScopeJS
    }

    companion object {
        private const val CONTENT_SCOPE_PLACEHOLDER = "\$CONTENT_SCOPE$"
        private const val USER_UNPROTECTED_DOMAINS_PLACEHOLDER = "\$USER_UNPROTECTED_DOMAINS$"
        private const val USER_PREFERENCES_PLACEHOLDER = "\$USER_PREFERENCES$"
    }

    private fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}
