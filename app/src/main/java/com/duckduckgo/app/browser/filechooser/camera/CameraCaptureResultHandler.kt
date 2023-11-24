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

package com.duckduckgo.app.browser.filechooser.camera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.FileProvider
import java.io.File
import java.lang.IllegalStateException

class RealCameraCaptureResultHandler : ActivityResultContract<Unit?, File?>() {

    private var interimImageLocation: File? = null

    override fun createIntent(
        context: Context,
        input: Unit?,
    ): Intent {
        val destinationForCapturedImage = destinationImageCaptureFile(context) ?: throw IllegalStateException("Unable to save images from camera")

        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            destinationForCapturedImage.also { newFile ->
                val safeUri = FileProvider.getUriForFile(context, "${context.packageName}.$PROVIDER_SUFFIX", newFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, safeUri)
            }
        }
    }

    private fun destinationImageCaptureFile(context: Context): File? {
        val topLevelDirectory: File = context.externalCacheDir ?: return null
        val cameraDataDir = File(topLevelDirectory, SUBDIRECTORY)
        cameraDataDir.mkdirs()
        val newFileName = "${System.currentTimeMillis()}.$IMAGE_FILE_EXTENSION"
        return File(cameraDataDir, newFileName).also {
            interimImageLocation = it
        }
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): File? {
        if (resultCode != Activity.RESULT_OK) {
            return null
        }

        return interimImageLocation
    }

    companion object {
        private const val SUBDIRECTORY = "browser-uploads"
        private const val PROVIDER_SUFFIX = "provider"
        private const val IMAGE_FILE_EXTENSION = "jpg"
    }
}
