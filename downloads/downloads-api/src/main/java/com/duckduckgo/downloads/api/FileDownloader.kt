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

import android.os.Environment
import androidx.annotation.AnyThread
import java.io.File
import java.io.Serializable

/** Interface for the starting point of a download. */
interface FileDownloader {

    /** Starts a download. Takes as parameters a [PendingFileDownload] containing all details about the file to be downloaded. */
    @AnyThread
    fun enqueueDownload(pending: PendingFileDownload)

    /** Data class for pending download.*/
    data class PendingFileDownload(
        val url: String,
        val contentDisposition: String? = null,
        val mimeType: String? = null,
        val subfolder: String,
        val directory: File = Environment.getExternalStoragePublicDirectory(subfolder),
        val isUrlCompressed: Boolean = false,
        val fileName: String? = null,
    ) : Serializable
}
