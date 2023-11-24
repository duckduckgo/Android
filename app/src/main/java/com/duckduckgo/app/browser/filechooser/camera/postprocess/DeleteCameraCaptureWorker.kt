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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Images captured by the camera to be uploaded through the WebView shouldn't be kept forever.
 * The URI for the image is passed to the WebView, but we don't know when it is safe to delete the file.
 *
 * This worker is responsible for deleting the file after a period of time.
 *
 * The typical use case is that:
 * - the user chooses to upload an image on a website
 * - user chooses to take a photo rather than use an existing image
 * - the camera app is launched
 * - a new file is created to store the image
 * - we pass the URI of the file to the camera app
 * - we schedule the file to be deleted using this worker
 */
@ContributesWorker(AppScope::class)
class DeleteCameraCaptureWorker(
    context: Context,
    workerParameters: WorkerParameters,
) :
    CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result {
        return withContext(dispatchers.io()) {
            deleteFile()
        }
    }

    private fun deleteFile(): Result {
        val fileUri = inputData.getString(KEY_FILE_URI) ?: return Result.failure()

        val file = File(fileUri)
        if (!file.exists()) {
            Timber.v("file doesn't exist; nothing to do here. file=%s", file.absolutePath)
            return Result.success()
        }

        Timber.d("time to delete the temporary captured image file %s", file)

        return if (file.delete()) {
            Timber.d("Successfully deleted the file %s", file.absolutePath)
            Result.success()
        } else {
            Timber.w("Failed to delete the file %s", file.absolutePath)

            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_FILE_URI = "fileUri"
        private const val MAX_ATTEMPTS = 10
    }
}
