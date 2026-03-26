/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.internal

import com.duckduckgo.app.global.api.ApiInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncService.Companion.SYNC_DEV_ENVIRONMENT_URL
import com.duckduckgo.sync.impl.SyncService.Companion.SYNC_PROD_ENVIRONMENT_URL
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ApiInterceptorPlugin::class,
)
class SyncInternalEnvInterceptor @Inject constructor(
    private val envDataStore: SyncInternalEnvDataStore,
) : ApiInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this
    override fun intercept(chain: Chain): Response {
        val useDevEnvironment = envDataStore.useSyncDevEnvironment

        if (useDevEnvironment && chain.request().url.toString().contains(SYNC_PROD_ENVIRONMENT_URL)) {
            val newRequest = chain.request().newBuilder()

            val changedUrl = chain.request().url.toString().replace(SYNC_PROD_ENVIRONMENT_URL, SYNC_DEV_ENVIRONMENT_URL)
            logcat { "Sync-Engine: environment changed to $changedUrl" }
            newRequest.url(changedUrl)
            return chain.proceed(newRequest.build())
        }

        return chain.proceed(chain.request())
    }
}
