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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Named

interface ContentScopeJSReader {
    fun getContentScopeJS(): String
}

abstract class GenericContentScopeJSReader {
    abstract val fileName: String

    private lateinit var contentScopeJS: String

    protected fun getContentScopeJSFile(): String {
        if (!this::contentScopeJS.isInitialized) {
            contentScopeJS = readResource(fileName).use { it?.readText() }.orEmpty()
        }
        return contentScopeJS
    }

    private fun readResource(resourceName: String): BufferedReader? {
        return javaClass.classLoader?.getResource(resourceName)?.openStream()?.bufferedReader()
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ContentScopeJSReader::class)
@Named("contentScope")
class RealContentScopeJSReader @Inject constructor() : GenericContentScopeJSReader(), ContentScopeJSReader {
    override val fileName: String
        get() = "contentScope.js"

    override fun getContentScopeJS(): String {
        return getContentScopeJSFile()
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ContentScopeJSReader::class)
@Named("adsJS")
class AdsContentScopeJSReader @Inject constructor() : GenericContentScopeJSReader(), ContentScopeJSReader {
    override val fileName: String
        get() = "adsjsContentScope.js"

    override fun getContentScopeJS(): String {
        return getContentScopeJSFile()
    }
}
