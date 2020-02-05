/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.downloader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class NetworkFileDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val manager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
        if (manager != null){
            val downloadStateQuery = DownloadManager.Query().setFilterById(intent!!.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0))
            val downloadState = manager.query(downloadStateQuery)
            while (downloadState.moveToNext()) {
                val statusIndex = downloadState.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val reasonIndex = downloadState.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                val status = downloadState.getInt(statusIndex)
                val reason = downloadState.getInt(reasonIndex)
                Timber.d("Download completed with state $status because $reason")
            }
            downloadState.close()
        }

    }
}