/*
 * Copyright (c) 2025 DuckDuckGo
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
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.mobile.android.R
import java.io.File
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

interface PdfPreviewGenerator {
    suspend fun generatePreview(pdfFile: File): Bitmap
}

class FileBasedPdfPreviewGenerator(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : PdfPreviewGenerator {

    @SuppressLint("AvoidIoUsage")
    override suspend fun generatePreview(pdfFile: File): Bitmap = withContext(dispatchers.io()) {
        val fileDescriptor = ParcelFileDescriptor.open(
            pdfFile,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )

        val pdfRenderer = PdfRenderer(fileDescriptor)

        try {
            val firstPage = pdfRenderer.openPage(0)

            // Create bitmap with first page dimensions
            val bitmap = Bitmap.createBitmap(
                firstPage.width,
                firstPage.height,
                Bitmap.Config.ARGB_8888,
            )

            // Fill bitmap with white background to handle transparent PDFs
            // (PdfRenderer renders with transparency, which becomes black in JPEG)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Render first page on top of white background
            firstPage.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
            )

            firstPage.close()

            // Scale to match design system dimensions (like WebView previews)
            val scaledBitmap = scaleToPreviewSize(bitmap)
            bitmap.recycle()

            scaledBitmap
        } finally {
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }

    @SuppressLint("AvoidComputationUsage")
    private suspend fun scaleToPreviewSize(bitmap: Bitmap): Bitmap {
        return withContext(dispatchers.computation()) {
            val scaledHeight = context.resources.getDimension(R.dimen.gridItemPreviewHeight).toPx()
            val scaledWidth = scaledHeight / bitmap.height * bitmap.width

            Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth.roundToInt(),
                scaledHeight.roundToInt(),
                false,
            )
        }
    }
}