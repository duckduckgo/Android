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

package com.duckduckgo.app.anr.internal.feature

import com.duckduckgo.app.anr.AnrPixelName.ANR_PIXEL
import com.duckduckgo.app.anr.internal.setting.CrashANRsRepository
import com.duckduckgo.app.anr.internal.store.AnrInternalEntity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Response

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class CrashANRsInterceptor @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val coroutineDispatcher: DispatcherProvider,
    private val repository: CrashANRsRepository,
) : PixelInterceptorPlugin, Interceptor {
    override fun getInterceptor(): Interceptor = this

    override fun intercept(chain: Chain): Response {
        val pixel = chain.request().url.pathSegments.last()
        if (pixel.contains(ANR_PIXEL.pixelName)) {
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                val url = chain.request().url
                val stackTrace = url.queryParameter("stackTrace") ?: return@launch
                val webview = url.queryParameter("webView") ?: return@launch
                val customTab = url.queryParameter("customTab") ?: return@launch
                repository.insertANR(AnrInternalEntity(stackTrace = stackTrace, webView = webview, customTab = customTab.toBoolean()))
            }
        }

        return chain.proceed(chain.request())
    }
}
