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

import android.net.Uri

/** Each failed download has a specific reason represented by a [DownloadFailReason] object. */
sealed class DownloadFailReason {

    object DownloadManagerDisabled : DownloadFailReason()
    object UnsupportedUrlType : DownloadFailReason()
    object Other : DownloadFailReason()
    object DataUriParseException : DownloadFailReason()
    object ConnectionRefused : DownloadFailReason()

    companion object {
        val DOWNLOAD_MANAGER_SETTINGS_URI: Uri = Uri.parse("package:com.android.providers.downloads")
    }
}
