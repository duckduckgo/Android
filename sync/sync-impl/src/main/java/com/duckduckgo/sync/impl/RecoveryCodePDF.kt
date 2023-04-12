/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo.Builder
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.ui.view.toPx
import com.duckduckgo.sync.impl.databinding.ViewRecoveryCodeBinding
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

interface RecoveryCodePDF {
    fun generateAndStoreRecoveryCodePDF(viewContext: Context, recoveryCode: Bitmap, recoveryCodeB64: String): File
}

@ContributesBinding(ActivityScope::class)
class RecoveryCodePDFImpl @Inject constructor() : RecoveryCodePDF {

    override fun generateAndStoreRecoveryCodePDF(viewContext: Context, recoveryCode: Bitmap, recoveryCodeB64: String): File {
        PdfDocument().apply {
            val inflater = LayoutInflater.from(viewContext)
            val page = startPage(Builder(a4PageWidth.toPx(), a4PageHeight.toPx(), 1).create())
            ViewRecoveryCodeBinding.inflate(inflater, null, false).apply {
                this.qrCodeImageView.setImageBitmap(recoveryCode)
                this.recoveryCodeText.text = recoveryCodeB64
                val measureWidth: Int = View.MeasureSpec.makeMeasureSpec(page.canvas.width, View.MeasureSpec.EXACTLY)
                val measuredHeight: Int = View.MeasureSpec.makeMeasureSpec(page.canvas.height, View.MeasureSpec.EXACTLY)
                this.root.measure(measureWidth, measuredHeight)
                this.root.layout(0, 0, page.canvas.width, page.canvas.height)
                this.root.draw(page.canvas)
            }
            finishPage(page)
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloads, PDF_FILE_NAME)
            writeTo(FileOutputStream(file))
            close()
            return file
        }
    }

    companion object {
        private const val PDF_FILE_NAME = "Sync Data Recovery - DuckDuckGo.pdf"
        private const val a4PageWidth = 612
        private const val a4PageHeight = 792
    }
}
