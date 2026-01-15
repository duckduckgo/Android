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

package com.duckduckgo.remote.messaging.impl.store

import android.content.Context
import com.bumptech.glide.Glide
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import java.io.File
import kotlinx.coroutines.withContext
import logcat.logcat

interface RemoteMessageImageStore {

    /**
     * Fetches and stores the image associated with the provided [message].
     */
    suspend fun fetchAndStoreImage(message: RemoteMessage?)

    /**
     * Returns the local file where the image for the active message is stored.
     * Must be called from a background thread (IO dispatcher).
     */
    suspend fun getLocalImageFilePath(): String?

    /**
     * Clears the stored image file.
     */
    suspend fun clearStoredImageFile()
}

class GlideRemoteMessageImageStore(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : RemoteMessageImageStore {

    override suspend fun fetchAndStoreImage(message: RemoteMessage?) {
        val imageUrl = message?.content?.getImageUrl()

        if (imageUrl.isNullOrEmpty()) {
            logcat(tag = "RadoiuC") { "RMF: No image URL to prefetch for message: ${message?.id}" }
            return
        }

        withContext(dispatcherProvider.io()) {
            runCatching {
                logcat(tag = "RadoiuC") { "RMF: Prefetching image: $imageUrl for message: ${message.id}" }

                val downloadedFile = Glide.with(context)
                    .asFile()
                    .load(imageUrl)
                    .submit()
                    .get()

                val permanentFile = getImageFile()
                downloadedFile.copyTo(permanentFile, overwrite = true)

                logcat(tag = "RadoiuC") { "RMF: Successfully saved image to permanent storage: ${permanentFile.absolutePath}" }
            }.onFailure { error ->
                logcat(tag = "RadoiuC") { "RMF: Failed to prefetch image $imageUrl: ${error.message}" }
            }
        }
    }

    override suspend fun getLocalImageFilePath(): String? {
        return withContext(dispatcherProvider.io()) {
            val file = getImageFile()
            if (file.exists()) file.absolutePath else null
        }
    }

    override suspend fun clearStoredImageFile() {
        withContext(dispatcherProvider.io()) {
            runCatching {
                getImageFile().let {
                    if (it.exists()) {
                        logcat(tag = "RadoiuC") { "RMF: clear the stored image file" }
                        it.delete()
                    }
                }
            }.onFailure {
                logcat(tag = "RadoiuC") { "Failed to clear the stored image file" }
            }
        }
    }

    private fun getImageFile(): File {
        return File(context.filesDir, REMOTE_IMAGE_FILE)
    }

    private fun Content.getImageUrl(): String? {
        return when (this) {
            is Content.Small -> null
            is Content.Medium -> this.imageUrl
            is Content.BigSingleAction -> this.imageUrl
            is Content.BigTwoActions -> this.imageUrl
            is Content.PromoSingleAction -> this.imageUrl
            is Content.CardsList -> this.imageUrl
        }
    }

    companion object {
        private const val REMOTE_IMAGE_FILE = "active_message_remote_image.png"
    }
}
