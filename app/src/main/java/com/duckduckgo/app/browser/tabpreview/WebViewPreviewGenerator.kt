/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.tabpreview

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.core.view.drawToBitmap
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.withContext

interface WebViewPreviewGenerator {
    suspend fun generatePreview(webView: WebView): Bitmap
}

class FileBasedWebViewPreviewGenerator(private val dispatchers: DispatcherProvider) : WebViewPreviewGenerator {

    override suspend fun generatePreview(webView: WebView): Bitmap {
        disableScrollbars(webView)
        val fullSizeBitmap = createBitmap(webView)
        val scaledBitmap = scaleBitmap(fullSizeBitmap)
        enableScrollbars(webView)
        return scaledBitmap
    }

    private suspend fun createBitmap(webView: WebView): Bitmap {
        return withContext(dispatchers.computation()) {
            webView.drawToBitmap()
        }
    }

    private suspend fun enableScrollbars(webView: WebView) {
        withContext(dispatchers.main()) {
            webView.isVerticalScrollBarEnabled = true
            webView.isHorizontalScrollBarEnabled = true
        }
    }

    private suspend fun disableScrollbars(webView: WebView) {
        withContext(dispatchers.main()) {
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
        }
    }

    private suspend fun scaleBitmap(bitmap: Bitmap): Bitmap {
        return withContext(dispatchers.computation()) {
            return@withContext Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * COMPRESSION_RATIO).toInt(),
                (bitmap.height * COMPRESSION_RATIO).toInt(),
                false,
            )
        }
    }

    companion object {
        private const val COMPRESSION_RATIO = 0.5
    }
}
