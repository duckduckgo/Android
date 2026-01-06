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

package com.duckduckgo.remote.messaging.impl.network

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import kotlinx.coroutines.withContext
import logcat.logcat

interface RemoteMessageImagePrefetcher {

    suspend fun prefetchImage(message: RemoteMessage?)
}

class GlideRemoteMessageImagePrefetcher(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : RemoteMessageImagePrefetcher {

    override suspend fun prefetchImage(message: RemoteMessage?) {
        val imageUrl = message?.content?.getImageUrl() ?: return

        if (imageUrl.isBlank()) {
            logcat(tag = "RadoiuC") { "No image URL to prefetch for message: ${message.id}" }
            return
        }

        withContext(dispatcherProvider.io()) {
            runCatching {
                logcat(tag = "RadoiuC") { "Prefetching image: $imageUrl for message: ${message.id}" }

                Glide.with(context)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .preload()

                logcat(tag = "RadoiuC") { "Successfully prefetched image: $imageUrl" }
            }.onFailure { error ->
                logcat(tag = "RadoiuC") { "Failed to prefetch image $imageUrl: ${error.message}" }
            }
        }
    }

    private fun Content.getImageUrl(): String? {
        return when (this) {
            is Content.Small -> this.imageUrl
            is Content.Medium -> this.imageUrl
            is Content.BigSingleAction -> this.imageUrl
            is Content.BigTwoActions -> this.imageUrl
            is Content.PromoSingleAction -> this.imageUrl
            is Content.CardsList -> this.imageUrl
        }
    }
}
