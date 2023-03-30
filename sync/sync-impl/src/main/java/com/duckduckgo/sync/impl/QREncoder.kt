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
import android.graphics.*
import androidx.annotation.DimenRes
import com.google.zxing.BarcodeFormat.QR_CODE
import com.google.zxing.EncodeHintType
import com.journeyapps.barcodescanner.*
import javax.inject.*

interface QREncoder {
    fun encodeAsBitmap(
        textToEncode: String,
        width: Int,
        height: Int,
    ): Bitmap
}

class AppQREncoder constructor(
    private val context: Context,
    private val barcodeEncoder: BarcodeEncoder,
) : QREncoder {

    override fun encodeAsBitmap(textToEncode: String, @DimenRes width: Int, @DimenRes height: Int): Bitmap {
        return barcodeEncoder.encodeBitmap(
            textToEncode,
            QR_CODE,
            context.resources.getDimensionPixelSize(width),
            context.resources.getDimensionPixelSize(height),
            mapOf(EncodeHintType.MARGIN to 0),
        )
    }
}
