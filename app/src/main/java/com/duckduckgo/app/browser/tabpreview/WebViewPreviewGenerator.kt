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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.core.view.drawToBitmap
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.mobile.android.R
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

interface WebViewPreviewGenerator {
    suspend fun generatePreview(webView: WebView): Bitmap
}

class FileBasedWebViewPreviewGenerator(private val dispatchers: DispatcherProvider) : WebViewPreviewGenerator {

    override suspend fun generatePreview(webView: WebView): Bitmap {
        try {
            disableScrollbars(webView)
            val fullSizeBitmap = createBitmap(webView)

            val scaledHeight = webView.context.resources.getDimension(R.dimen.gridItemPreviewHeight).toPx()
            val scaledWidth = scaledHeight / fullSizeBitmap.height * fullSizeBitmap.width
            return scaleBitmap(fullSizeBitmap, scaledHeight.roundToInt(), scaledWidth.roundToInt())
        } finally {
            enableScrollbars(webView)
        }
    }

    @SuppressLint("AvoidComputationUsage")
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

    @SuppressLint("AvoidComputationUsage")
    private suspend fun scaleBitmap(bitmap: Bitmap, scaledHeight: Int, scaledWidth: Int): Bitmap {
        return withContext(dispatchers.computation()) {
            return@withContext Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight,
                false,
            )
        }
    }
}
