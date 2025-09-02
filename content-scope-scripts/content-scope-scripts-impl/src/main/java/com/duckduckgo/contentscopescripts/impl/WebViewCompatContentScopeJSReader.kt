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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.BufferedReader
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface WebViewCompatContentScopeJSReader {
    suspend fun getContentScopeJS(): String
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealWebViewCompatContentScopeJSReader @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) : WebViewCompatContentScopeJSReader {
    private lateinit var contentScopeJS: String

    override suspend fun getContentScopeJS(): String {
        if (!this@RealWebViewCompatContentScopeJSReader::contentScopeJS.isInitialized) {
            contentScopeJS = loadJs("contentScope.js")
        }
        return contentScopeJS
    }

    private suspend fun loadJs(resourceName: String): String = readResource(resourceName).use { it?.readText() }.orEmpty()

    private suspend fun readResource(resourceName: String): BufferedReader? {
        return withContext(dispatcherProvider.io()) {
            javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
        }
    }
}
