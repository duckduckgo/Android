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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration.AutofillJsConfigType
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import javax.inject.Inject

interface AutofillJavascriptLoader {
    suspend fun getAutofillJavascript(): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAutofillJavascriptLoader @Inject constructor(
    private val filenameProvider: AutofillJavascriptEnvironmentConfiguration,
    private val dispatchers: DispatcherProvider,
) : AutofillJavascriptLoader {

    private val productionJavascript: String by lazy { loadJs(AUTOFILL_JS_FILENAME) }
    private val debugJavascript: String by lazy { loadJs(AUTOFILL_JS_DEBUG_FILENAME) }

    override suspend fun getAutofillJavascript(): String {
        return withContext(dispatchers.io()) {
            when (filenameProvider.getConfigType()) {
                AutofillJsConfigType.Production -> productionJavascript
                AutofillJsConfigType.Debug -> debugJavascript
            }
        }
    }

    private fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }

    companion object {
        const val AUTOFILL_JS_FILENAME = "autofill.js"
        const val AUTOFILL_JS_DEBUG_FILENAME = "autofill-debug.js"
    }
}
