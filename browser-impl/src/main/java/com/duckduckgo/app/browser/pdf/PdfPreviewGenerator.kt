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

package com.duckduckgo.app.browser.pdf

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import androidx.core.view.drawToBitmap
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.R
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.roundToInt

interface PdfPreviewGenerator {
    /**
     * Generates a bitmap preview of the given view.
     *
     * @param view the view to generate a preview for
     * @return a bitmap representing the preview of the view
     */
    suspend fun generatePreview(view: View): Bitmap
}

@ContributesBinding(AppScope::class)
class FileBasedPdfPreviewGenerator @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : PdfPreviewGenerator {

    @SuppressLint("AvoidComputationUsage")
    override suspend fun generatePreview(view: View): Bitmap {
        return withContext(dispatchers.computation()) {
            val fullBitmap = view.drawToBitmap()
            val scaledHeight = view.context.resources.getDimension(R.dimen.gridItemPreviewHeight)
            val scaledWidth = scaledHeight / fullBitmap.height * fullBitmap.width
            Bitmap.createScaledBitmap(fullBitmap, scaledWidth.roundToInt(), scaledHeight.roundToInt(), false)
        }
    }
}
