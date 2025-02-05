/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service.mapper

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.http.GET
import retrofit2.http.Url

@ContributesServiceApi(AppScope::class)
interface AssetLinksService {
    @GET
    suspend fun getAssetLinks(
        @Url assetLinkUrl: String,
    ): List<AssetLink>
}

data class AssetLink(
    val relation: List<String>,
    val target: AssetLinkTarget,
)

data class AssetLinkTarget(
    val namespace: String,
    val package_name: String?,
    val sha256_cert_fingerprints: List<String>?,

)
