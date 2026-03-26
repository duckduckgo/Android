/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.downloads.api

import android.content.Context
import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Interface containing download callbacks. */
interface DownloadStateListener {
    /**
     * Data stream that sequentially emits commands of type [DownloadCommand].
     */
    fun commands(): Flow<DownloadCommand>
}

interface DownloadConfirmationDialogListener {
    fun continueDownload(pendingFileDownload: PendingFileDownload)
    fun cancelDownload()
}

interface DownloadConfirmation {
    fun instance(pendingDownload: PendingFileDownload): BottomSheetDialogFragment
}

interface DownloadsFileActions {
    fun openFile(applicationContext: Context, file: File): Boolean
    fun shareFile(applicationContext: Context, file: File): Boolean
}
