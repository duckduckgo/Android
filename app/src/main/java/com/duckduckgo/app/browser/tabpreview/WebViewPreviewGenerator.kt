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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


interface WebViewPreviewGenerator {

    suspend fun generatePreview(webView: WebView): Bitmap

}

class FileBasedWebViewPreviewGenerator : WebViewPreviewGenerator {

    override suspend fun generatePreview(webView: WebView): Bitmap {
        val fullSizeBitmap = convertWebViewToBitmap(webView)
        val scaledBitmap = scaleBitmap(fullSizeBitmap, webView)
        Timber.d("Full size bitmap: ${fullSizeBitmap.byteCount}, reduced size: ${scaledBitmap.byteCount}")
        return scaledBitmap

    }

    private suspend fun convertWebViewToBitmap(webView: WebView): Bitmap {
        return withContext(Dispatchers.Main) {
            disableScrollbars(webView)
            val bm = webView.drawToBitmap()
            enableScrollbars(webView)
            return@withContext bm
        }
    }

    private fun enableScrollbars(webView: WebView) {
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true
    }

    private fun disableScrollbars(webView: WebView) {
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
    }

    private suspend fun scaleBitmap(fullSize: Bitmap, webView: WebView): Bitmap {
        return withContext(Dispatchers.IO) {
            return@withContext Bitmap.createScaledBitmap(
                fullSize,
                (webView.width * COMPRESSION_PERCENTAGE / 100),
                (webView.height * COMPRESSION_PERCENTAGE / 100),
                false
            )
        }
    }

    companion object {
        private const val COMPRESSION_PERCENTAGE = 50
    }
}