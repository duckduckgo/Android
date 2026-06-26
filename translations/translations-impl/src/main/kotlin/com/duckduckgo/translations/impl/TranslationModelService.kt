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

package com.duckduckgo.translations.impl

import com.duckduckgo.anvil.annotations.ContributesServiceApi
import com.duckduckgo.di.scopes.AppScope
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Prototype model source: Mozilla's GCS (SPEC-2 §5.1a). Both calls take a full [url] so we can hit the
 * manifest and the per-file objects.
 */
@ContributesServiceApi(AppScope::class)
interface TranslationModelService {

    @GET
    suspend fun manifest(@Url url: String): ModelsManifest

    @Streaming
    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody
}
