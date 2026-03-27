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

package com.duckduckgo.youtubeadblocking.impl

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.youtubeadblocking.api.YouTubeAdBlocking
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealYouTubeAdBlocking @Inject constructor(
    private val youTubeAdBlockingFeature: YouTubeAdBlockingFeature,
    private val settingsProvider: YouTubeAdBlockingSettingsProvider,
    private val requestInterceptor: YouTubeAdBlockingRequestInterceptor,
    private val dispatcherProvider: DispatcherProvider,
) : YouTubeAdBlocking {

    override suspend fun isEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            youTubeAdBlockingFeature.self().isEnabled()
        }
    }

    override suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
    ): WebResourceResponse? {
        if (!isEnabled()) return null
        if (settingsProvider.injectMethod != InjectMethod.INTERCEPT) return null
        return requestInterceptor.intercept(request, url)
    }
}
