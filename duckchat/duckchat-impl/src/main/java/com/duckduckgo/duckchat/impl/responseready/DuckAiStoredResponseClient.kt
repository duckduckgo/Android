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

package com.duckduckgo.duckchat.impl.responseready

import com.duckduckgo.codegen.annotations.ContributesNonCachingServiceApi
import com.duckduckgo.di.scopes.AppScope
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Asks Sync whether a Duck.ai response has been stored yet. Only the status code matters: the body is declared as [Unit],
 * so Retrofit closes it without reading or parsing it.
 */
@ContributesNonCachingServiceApi(AppScope::class)
interface DuckAiStoredResponseClient {

    @GET("https://sync.duckduckgo.com/sync/ai_chat_streams/{responseId}")
    suspend fun getStoredResponse(@Path("responseId") responseId: String): Response<Unit>
}
