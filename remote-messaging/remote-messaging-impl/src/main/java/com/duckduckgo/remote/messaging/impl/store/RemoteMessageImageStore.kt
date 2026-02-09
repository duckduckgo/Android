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
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File

interface RemoteMessageImageStore {

    /**
     * Fetches and stores the image associated with the provided [message].
     * Stores one image per surface specified in the message.
     */
    suspend fun fetchAndStoreImage(message: RemoteMessage?)

    /**
     * Returns the local file where the image for the active message and surface is stored.
     * Must be called from a background thread (IO dispatcher).
     */
    suspend fun getLocalImageFilePath(surface: Surface): String?

    /**
     * Clears the stored image file for the specified surface.
     */
    suspend fun clearStoredImageFile(surface: Surface)
}

class GlideRemoteMessageImageStore(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : RemoteMessageImageStore {

    override suspend fun fetchAndStoreImage(message: RemoteMessage?) {
        val imageUrl = message?.content?.getImageUrl()

        message?.surfaces?.forEach { surface ->
            deleteStoredImage(surface)
        }

        if (imageUrl.isNullOrEmpty()) {
            logcat { "RMF: No image URL to prefetch for message: ${message?.id}" }
            return
        }

        withContext(dispatcherProvider.io()) {
            // Store one image per surface
            message.surfaces.forEach { surface ->
                runCatching {
                    logcat { "RMF: Prefetching image: $imageUrl for message: ${message.id} surface: ${surface.jsonValue}" }

                    val downloadedFile = Glide.with(context)
                        .asFile()
                        .load(imageUrl)
                        .submit()
                        .get()

                    val permanentFile = getImageFile(surface)
                    downloadedFile.copyTo(permanentFile, overwrite = true)

                    logcat { "RMF: Successfully saved image to permanent storage: ${permanentFile.absolutePath}" }
                }.onFailure { error ->
                    logcat { "RMF: Failed to prefetch image $imageUrl for surface ${surface.jsonValue}: ${error.message}" }
                }
            }
        }
    }

    override suspend fun getLocalImageFilePath(surface: Surface): String? {
        return withContext(dispatcherProvider.io()) {
            val file = getImageFile(surface)
            if (file.exists()) file.absolutePath else null
        }
    }

    override suspend fun clearStoredImageFile(surface: Surface) {
        deleteStoredImage(surface)
    }

    private suspend fun deleteStoredImage(surface: Surface) {
        withContext(dispatcherProvider.io()) {
            runCatching {
                getImageFile(surface).let {
                    if (it.exists()) {
                        logcat { "RMF: Clear the stored image file for surface: ${surface.jsonValue}" }
                        it.delete()
                    }
                }
            }.onFailure {
                logcat { "Failed to clear the stored image file for surface: ${surface.jsonValue}" }
            }
        }
    }

    private fun getImageFile(surface: Surface): File {
        val fileName = "${REMOTE_IMAGE_FILE_PREFIX}_${surface.jsonValue}.png"
        return File(context.filesDir, fileName)
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
        private const val REMOTE_IMAGE_FILE_PREFIX = "active_message_remote_image"
    }
}
