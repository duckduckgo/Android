/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.contextual

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class PageContextData(
    val serializedPageData: String,
    val collectedAtMs: Long,
)

interface PageContextRepository {
    fun update(serializedPageData: String)
    fun clear()
    fun getLatestPageContext(): PageContextData?
}

@ContributesBinding(AppScope::class)
class RealPageContextRepository @Inject constructor() : PageContextRepository {
    @Volatile
    private var latestPageContext: PageContextData? = null

    override fun update(serializedPageData: String) {
        latestPageContext = PageContextData(serializedPageData, System.currentTimeMillis())
    }

    override fun clear() {
        latestPageContext = null
    }

    override fun getLatestPageContext(): PageContextData? = latestPageContext
}
