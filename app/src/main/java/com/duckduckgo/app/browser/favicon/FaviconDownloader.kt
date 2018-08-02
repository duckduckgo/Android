/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.bumptech.glide.Glide
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.view.toPx
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface FaviconDownloader {

    fun download(currentPageUrl: Uri): Single<Bitmap>
}

class GlideFaviconDownloader @Inject constructor(private val context: Context) : FaviconDownloader {

    override fun download(currentPageUrl: Uri): Single<Bitmap> {

        return Single.fromCallable {

            val faviconUrl = currentPageUrl.faviconLocation() ?: throw IllegalArgumentException("Invalid favicon currentPageUrl")
            val desiredImageSizePx = DESIRED_IMAGE_SIZE_DP.toPx()

            Glide.with(context)
                .asBitmap()
                .load(faviconUrl)
                .submit(desiredImageSizePx, desiredImageSizePx)
                .get(TIMEOUT_PERIOD_SECONDS, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val DESIRED_IMAGE_SIZE_DP = 24
        private const val TIMEOUT_PERIOD_SECONDS: Long = 3
    }
}
