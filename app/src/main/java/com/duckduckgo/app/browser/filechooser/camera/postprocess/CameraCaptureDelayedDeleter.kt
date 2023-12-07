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

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Inject

interface CameraCaptureDelayedDeleter {
    fun scheduleDeletion(file: File)
}

@ContributesBinding(FragmentScope::class)
class WorkManagerCameraCaptureDelayedDeleter @Inject constructor(
    private val workManager: WorkManager,
) : CameraCaptureDelayedDeleter {

    override fun scheduleDeletion(file: File) {
        val workRequest = OneTimeWorkRequestBuilder<DeleteCameraCaptureWorker>()
            .setInputData(
                workDataOf(
                    DeleteCameraCaptureWorker.KEY_FILE_URI to file.absolutePath,
                ),
            )
            .setInitialDelay(INITIAL_DELAY_SECONDS, SECONDS)
            .build()
        workManager.enqueue(workRequest)
    }

    companion object {
        private const val INITIAL_DELAY_SECONDS = 60L
    }
}
