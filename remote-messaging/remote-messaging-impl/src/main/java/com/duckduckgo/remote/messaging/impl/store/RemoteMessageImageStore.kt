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
     * Returns the local file path for the image of the list item [itemId] in [message], or null when the message
     * has no per-item images or the file doesn't exist.
     */
    suspend fun getItemImageFilePath(
        message: RemoteMessage,
        itemId: String,
    ): String?
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

    override suspend fun getItemImageFilePath(
        message: RemoteMessage,
        itemId: String,
    ): String? {
        val imagesDir = message.content.itemImagesDir() ?: return null
        return withContext(dispatcherProvider.io()) {
            val file = itemImageFile(imagesDir, itemId)
            if (file.exists()) file.absolutePath else null
        }
    }

    private suspend fun fetchAndStoreCardItemImages(message: RemoteMessage?) {
        clearItemImages(ACTIONABLE_ITEM_IMAGES_DIR)
        val content = message?.content ?: return
        val imagesDir = content.itemImagesDir() ?: return
        val listItems = when (content) {
            is Content.CardsList -> {
                clearItemImages(imagesDir)
                content.listItems
            }
            is Content.ActionableItems -> content.listItems
            else -> return
        }

        val itemsWithImages = listItems
            .filterIsInstance<CardItem.ListItem>()
            .filter { !it.imageUrl.isNullOrEmpty() }

        if (itemsWithImages.isEmpty()) return

        withContext(dispatcherProvider.io()) {
            itemsWithImages.map { item ->
                async {
                    val targetFile = itemImageFile(imagesDir, item.id)
                    targetFile.parentFile?.mkdirs()
                    downloadImageToFile(item.imageUrl.orEmpty(), targetFile)
                }
            }.awaitAll()
        }
    }

    private suspend fun clearItemImages(imagesDir: String) {
        withContext(dispatcherProvider.io()) {
            runCatching {
                val dir = File(context.filesDir, imagesDir)
                if (dir.exists()) {
                    logcat { "RMF: Clearing all item images in $imagesDir" }
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

    private fun itemImageFile(
        imagesDir: String,
        itemId: String,
    ): File = File(context.filesDir, "$imagesDir/$itemId.png")

    private fun Content.itemImagesDir(): String? {
        return when (this) {
            is Content.CardsList -> CARD_ITEM_IMAGES_DIR
            is Content.ActionableItems -> ACTIONABLE_ITEM_IMAGES_DIR
            else -> null
        }
    }

    private fun Content.getImageUrl(): String? {
        return when (this) {
            is Content.Small -> null
            is Content.Medium -> this.imageUrl
            is Content.BigSingleAction -> this.imageUrl
            is Content.BigTwoActions -> this.imageUrl
            is Content.PromoSingleAction -> this.imageUrl
            is Content.CardsList -> this.imageUrl
            is Content.ActionableItems -> null
        }
    }

    companion object {
        private const val REMOTE_IMAGE_FILE_PREFIX = "active_message_remote_image"
        private const val CARD_ITEM_IMAGES_DIR = "rmf_card_item_images"
        private const val ACTIONABLE_ITEM_IMAGES_DIR = "rmf_actionable_item_images"
    }
}
