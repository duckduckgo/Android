/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DimenRes
import androidx.core.graphics.drawable.toBitmap
import com.duckduckgo.app.browser.favicon.FaviconPersister
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister
import com.duckduckgo.app.global.view.generateDefaultDrawable
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class WidgetFaviconProvider @Inject constructor(
    private val context: Context,
    private val faviconPersister: FaviconPersister,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * Resolves a favicon file to display in the favorites widget for the given [domain].
     *
     * Lookup order:
     * 1. Existing persisted real favicon.
     * 2. Previously cached widget placeholder.
     * 3. Newly generated placeholder (stored on disk).
     *
     * Stale widget-sized placeholders previously mis-persisted into the main favicons
     * directory by older app versions are cleaned up as part of this lookup.
     */
    suspend fun getOrGenerateWidgetFavicon(
        domain: String,
        placeholderSizePx: Int,
        @DimenRes placeholderCornerRadius: Int,
    ): File? = withContext(dispatcherProvider.io()) {
        // step 1: check if any file (real favicon or placeholder) already exists on disk to avoid fetching/generating it again
        faviconPersister.faviconFile(
            FileBasedFaviconPersister.FAVICON_PERSISTED_DIR,
            FileBasedFaviconPersister.NO_SUBFOLDER,
            domain,
        )?.let { file ->
            // Older app versions mistakenly persisted widget-sized placeholders into the real favicons
            // directory (https://app.asana.com/1/137249556945/project/414730916066338/task/1214072492090532).
            // Real favicons virtually never match the widget placeholder size exactly, so this is used
            // as a heuristic to detect and remove the stale file before falling through to placeholder logic.
            if (file.isSizedExactly(placeholderSizePx)) {
                file.delete()
            } else {
                return@withContext file
            }
        }

        // step 2: check if there is an existing placeholder cached and use it
        faviconPersister.faviconFile(
            FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR,
            FileBasedFaviconPersister.NO_SUBFOLDER,
            domain,
        )?.let {
            return@withContext it
        }

        // step 3: generate and save placeholder
        val placeholder = generateDefaultDrawable(
            context = context,
            domain = domain,
            cornerRadius = placeholderCornerRadius,
        ).toBitmap(placeholderSizePx, placeholderSizePx)

        faviconPersister.store(
            FileBasedFaviconPersister.FAVICON_WIDGET_PLACEHOLDERS_DIR,
            FileBasedFaviconPersister.NO_SUBFOLDER,
            placeholder,
            domain,
        )
    }

    private fun File.isSizedExactly(sizePx: Int): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return options.outWidth == sizePx && options.outHeight == sizePx
    }
}
