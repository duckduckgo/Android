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
import android.net.Uri
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.duckduckgo.app.browser.R
import java.io.File
import javax.inject.Inject

interface FaviconDownloader {
    fun getFaviconFromDisk(file: File): Bitmap?
    fun getFaviconFromUrl(uri: Uri): Bitmap?
    suspend fun loadFaviconToView(file: File, view: ImageView)
}

class GlideFaviconDownloader @Inject constructor(
    private val context: Context
) : FaviconDownloader {

    override fun getFaviconFromDisk(file: File): Bitmap? {
        return Glide.with(context)
            .asBitmap()
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .submit()
            .get()
    }

    override fun getFaviconFromUrl(uri: Uri): Bitmap? {
        return Glide.with(context)
            .asBitmap()
            .load(uri)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .submit()
            .get()
    }

    override suspend fun loadFaviconToView(file: File, view: ImageView) {
        Glide.with(context)
            .load(file)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .placeholder(R.drawable.ic_globe_gray_16dp)
            .error(R.drawable.ic_globe_gray_16dp)
            .into(view)
    }

}
