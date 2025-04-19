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

package com.duckduckgo.installation.impl.installer.fullpackage.feature

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.installation.impl.installer.fullpackage.InstallSourceFullPackageStore.IncludedPackages
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject

interface InstallSourceFullPackageListJsonParser {
    suspend fun parseJson(json: String?): IncludedPackages
}

@ContributesBinding(AppScope::class)
class InstallSourceFullPackageListJsonParserImpl @Inject constructor() : InstallSourceFullPackageListJsonParser {

    private val jsonAdapter by lazy { buildJsonAdapter() }

    private fun buildJsonAdapter(): JsonAdapter<SettingsJson> {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return moshi.adapter(SettingsJson::class.java)
    }

    override suspend fun parseJson(json: String?): IncludedPackages {
        if (json == null) return IncludedPackages()

        return kotlin.runCatching {
            val parsed = jsonAdapter.fromJson(json)
            return parsed?.asIncludedPackages() ?: IncludedPackages()
        }.getOrDefault(IncludedPackages())
    }

    private fun SettingsJson.asIncludedPackages(): IncludedPackages {
        return IncludedPackages(includedPackages.map { it })
    }

    private data class SettingsJson(
        val includedPackages: List<String>,
    )
}
