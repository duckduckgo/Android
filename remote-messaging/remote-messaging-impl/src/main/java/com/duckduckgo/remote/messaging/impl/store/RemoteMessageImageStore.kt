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
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import logcat.logcat
import java.io.File

interface RemoteMessageImageStore {

    /**
     * Fetches and stores the image associated with the provided [message].
     * Stores one image per surface specified in the message.
     * For CardsList messages, also fetches and caches per-item images.
     */
    suspend fun fetchAndStoreImages(message: RemoteMessage?)

    /**
     * Returns the local file where the image for the active message and surface is stored.
     * Must be called from a background thread (IO dispatcher).
     */
    suspend fun getLocalImageFilePath(surface: Surface): String?

    /**
     * Clears the stored image file for the specified surface.
     */
    suspend fun clearStoredImageFile(surface: Surface)

    /**
     * Returns the local file path for a card item image, or null if it doesn't exist.
     */
    suspend fun getCardItemImageFilePath(itemId: String): String?
}

class GlideRemoteMessageImageStore(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : RemoteMessageImageStore {

    override suspend fun fetchAndStoreImages(message: RemoteMessage?) {
        val imageUrl = message?.content?.getImageUrl()

        message?.surfaces?.forEach { surface ->
            deleteStoredImage(surface)
        }

        if (!imageUrl.isNullOrEmpty()) {
            withContext(dispatcherProvider.io()) {
                message.surfaces.forEach { surface ->
                    downloadImageToFile(imageUrl, getImageFile(surface))
                }
            }
        }

        fetchAndStoreCardItemImages(message)
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

    override suspend fun getCardItemImageFilePath(itemId: String): String? {
        return withContext(dispatcherProvider.io()) {
            val file = getCardItemImageFile(itemId)
            if (file.exists()) file.absolutePath else null
        }
    }

    private suspend fun fetchAndStoreCardItemImages(message: RemoteMessage?) {
        val cardsList = message?.content as? Content.CardsList ?: return

        clearAllCardItemImages()
        val itemsWithImages = cardsList.listItems
            .filterIsInstance<CardItem.ListItem>()
            .filter { !it.imageUrl.isNullOrEmpty() }

        if (itemsWithImages.isEmpty()) return

        withContext(dispatcherProvider.io()) {
            itemsWithImages.map { item ->
                async {
                    val targetFile = getCardItemImageFile(item.id)
                    targetFile.parentFile?.mkdirs()
                    downloadImageToFile(item.imageUrl.orEmpty(), targetFile)
                }
            }.awaitAll()
        }
    }

    private suspend fun clearAllCardItemImages() {
        withContext(dispatcherProvider.io()) {
            runCatching {
                val dir = File(context.filesDir, CARD_ITEM_IMAGES_DIR)
                if (dir.exists()) {
                    logcat { "RMF: Clearing all card item images" }
                    dir.deleteRecursively()
                }
            }.onFailure {
                logcat { "RMF: Failed to clear card item images: ${it.message}" }
            }
        }
    }

    private fun downloadImageToFile(imageUrl: String, targetFile: File) {
        runCatching {
            logcat { "RMF: Prefetching image: $imageUrl -> ${targetFile.name}" }
            val downloadedFile = Glide.with(context)
                .asFile()
                .load(imageUrl)
                .submit()
                .get()
            downloadedFile.copyTo(targetFile, overwrite = true)
            logcat { "RMF: Successfully saved image: ${targetFile.absolutePath}" }
        }.onFailure { error ->
            logcat { "RMF: Failed to prefetch image $imageUrl: ${error.message}" }
        }
    }

    private fun getCardItemImageFile(itemId: String): File {
        return File(context.filesDir, "$CARD_ITEM_IMAGES_DIR/$itemId.png")
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
        private const val CARD_ITEM_IMAGES_DIR = "rmf_card_item_images"
    }
}
