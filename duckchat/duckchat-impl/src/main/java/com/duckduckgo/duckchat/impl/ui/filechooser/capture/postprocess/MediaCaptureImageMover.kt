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

package com.duckduckgo.duckchat.impl.ui.filechooser.capture.postprocess

import android.content.Context
import android.net.Uri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface MediaCaptureImageMover {
    suspend fun moveInternal(contentUri: Uri): File?
}

@ContributesBinding(ActivityScope::class)
class RealMediaCaptureImageMover @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : MediaCaptureImageMover {

    override suspend fun moveInternal(contentUri: Uri): File? {
        return withContext(dispatchers.io()) {
            val newDestinationDirectory = File(context.cacheDir, SUBDIRECTORY_NAME)
            newDestinationDirectory.mkdirs()

            val filename = contentUri.lastPathSegment ?: return@withContext null

            val newDestinationFile = File(newDestinationDirectory, filename)
            context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                FileOutputStream(newDestinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            newDestinationFile
        }
    }

    companion object {
        private const val SUBDIRECTORY_NAME = "browser-uploads"
    }
}
