/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.favicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

interface FaviconDownloader {
    suspend fun getFaviconFromDisk(file: File): Bitmap?
    suspend fun getFaviconFromDisk(
        file: File,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap?

    suspend fun getFaviconFromUrl(uri: Uri): Bitmap?
}

class GlideFaviconDownloader @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : FaviconDownloader {

    override suspend fun getFaviconFromDisk(file: File): Bitmap? = withContext(dispatcherProvider.io()) {
        if (androidBrowserConfigFeature.glideSuspend().isEnabled()) {
            getFaviconFromDiskAsync(file)
        } else {
            getFaviconFromDiskSync(file)
        }
    }

    override suspend fun getFaviconFromDisk(
        file: File,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap? = withContext(dispatcherProvider.io()) {
        if (androidBrowserConfigFeature.glideSuspend().isEnabled()) {
            getFaviconFromDiskAsync(file, cornerRadius, width, height)
        } else {
            getFaviconFromDiskSync(file, cornerRadius, width, height)
        }
    }

    override suspend fun getFaviconFromUrl(uri: Uri): Bitmap? = withContext(dispatcherProvider.io()) {
        if (androidBrowserConfigFeature.glideSuspend().isEnabled()) {
            getFaviconFromUrlAsync(uri)
        } else {
            getFaviconFromUrlSync(uri)
        }
    }

    private suspend fun getFaviconFromDiskAsync(file: File): Bitmap? = runCatching {
        Glide.with(context).asBitmap().load(file).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).awaitBitmap(context)
    }.getOrNull()

    private suspend fun getFaviconFromDiskAsync(
        file: File,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap? = kotlin.runCatching {
        Glide.with(context)
            .asBitmap()
            .load(file)
            .transform(RoundedCorners(cornerRadius))
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .awaitBitmap(context, width, height)
    }.getOrNull()

    private suspend fun getFaviconFromUrlAsync(uri: Uri): Bitmap? = runCatching {
        Glide.with(context).asBitmap().load(uri).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).awaitBitmap(context)
    }.getOrNull()

    private suspend fun RequestBuilder<Bitmap>.awaitBitmap(
        context: Context,
        width: Int,
        height: Int,
    ): Bitmap? = suspendCancellableCoroutine { continuation ->
        kotlin.runCatching {
            val target = object : CustomTarget<Bitmap>(width, height) {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?,
                ) {
                    continuation.safeResume(resource, null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    continuation.safeResume(null, null)
                }
            }
            val request = this.into(target)

            continuation.invokeOnCancellation {
                Glide.with(context).clear(request)
            }
        }.onFailure {
            continuation.safeResume(null, null)
        }
    }

    private suspend fun RequestBuilder<Bitmap>.awaitBitmap(context: Context): Bitmap? = suspendCancellableCoroutine { continuation ->
        kotlin.runCatching {
            val target = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?,
                ) {
                    continuation.safeResume(resource, null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    continuation.safeResume(null, null)
                }
            }
            val request = this.into(target)

            continuation.invokeOnCancellation {
                Glide.with(context).clear(request)
            }
        }.onFailure {
            continuation.safeResume(null, null)
        }
    }

    private fun <T> CancellableContinuation<T>.safeResume(
        value: T,
        onCancellation: ((cause: Throwable) -> Unit)?,
    ) {
        if (isActive) {
            resume(value, onCancellation)
        }
    }

    private fun getFaviconFromDiskSync(file: File): Bitmap? = runCatching {
        Glide.with(context).asBitmap().load(file).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).submit().get()
    }.getOrNull()

    private fun getFaviconFromDiskSync(
        file: File,
        cornerRadius: Int,
        width: Int,
        height: Int,
    ): Bitmap? = runCatching {
        Glide.with(context)
            .asBitmap()
            .load(file)
            .transform(RoundedCorners(cornerRadius))
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .submit(width, height)
            .get()
    }.getOrNull()

    private fun getFaviconFromUrlSync(uri: Uri): Bitmap? = runCatching {
        Glide.with(context).asBitmap().load(uri).diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true).submit().get()
    }.getOrNull()
}
