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

package com.duckduckgo.app.history

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface SaveToHistory {

    fun saveToHistory(url: String, title: String?)
}

@ContributesBinding(AppScope::class)
class RealSaveToHistory @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : SaveToHistory {
    override fun saveToHistory(
        url: String,
        title: String?,
    ) {
        appCoroutineScope.launch {
            val ddgUrl = duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
            val query = if (ddgUrl) duckDuckGoUrlDetector.extractQuery(url) else null

            historyRepository.saveToHistory(url, title, query, query != null)
        }
    }
}
