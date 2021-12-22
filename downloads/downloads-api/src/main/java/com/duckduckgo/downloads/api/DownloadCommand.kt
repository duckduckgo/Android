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

import androidx.annotation.StringRes

/** Specific download commands used to display messages during various download stages. */
sealed class DownloadCommand(@StringRes val messageId: Int, val showNotification: Boolean) {
    class ShowDownloadStartedMessage(
        @StringRes messageId: Int,
        showNotification: Boolean,
        val fileName: String
    ) : DownloadCommand(messageId, showNotification)
    class ShowDownloadSuccessMessage(
        @StringRes messageId: Int,
        showNotification: Boolean,
        val fileName: String,
        val filePath: String,
        val mimeType: String? = null
    ) : DownloadCommand(messageId, showNotification)
    class ShowDownloadFailedMessage(
        @StringRes messageId: Int,
        showNotification: Boolean,
        val showEnableDownloadManagerAction: Boolean
    ) : DownloadCommand(messageId, showNotification)
}
