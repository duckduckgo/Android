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

package com.duckduckgo.app.browser.filechooser.camera.postprocess

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface CameraCaptureImageMover {
    suspend fun moveInternal(interimFile: File): File
}

@ContributesBinding(FragmentScope::class)
class RealCameraCaptureImageMover @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CameraCaptureImageMover {

    override suspend fun moveInternal(interimFile: File): File {
        return withContext(dispatchers.io()) {
            val newDestinationDirectory = File(context.cacheDir, SUBDIRECTORY_NAME)
            newDestinationDirectory.mkdirs()
            val newDestinationFile = File(newDestinationDirectory, interimFile.name)

            FileInputStream(interimFile).use { inputStream ->
                FileOutputStream(newDestinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    interimFile.delete()
                }
            }

            newDestinationFile
        }
    }

    companion object {
        private const val SUBDIRECTORY_NAME = "browser-uploads"
    }
}
