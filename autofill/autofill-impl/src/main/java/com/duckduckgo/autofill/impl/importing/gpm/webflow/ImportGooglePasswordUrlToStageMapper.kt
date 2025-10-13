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
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface ImportGooglePasswordUrlToStageMapper {
    suspend fun getStage(url: String?): String
}

@ContributesBinding(FragmentScope::class)
class ImportGooglePasswordUrlToStageMapperImpl @Inject constructor(
    private val importPasswordConfigStore: AutofillImportPasswordConfigStore,
) : ImportGooglePasswordUrlToStageMapper {

    override suspend fun getStage(url: String?): String {
        val config = importPasswordConfigStore.getConfig()
        val stage = config.urlMappings.firstOrNull { url?.startsWith(it.url) == true }?.key ?: UNKNOWN
        return stage.also { logcat { "Mapped as stage $it for $url" } }
    }

    companion object {
        const val UNKNOWN = "webflow-unknown"
    }
}
