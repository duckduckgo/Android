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

package com.duckduckgo.downloads.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.FileDownloadManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import okhttp3.ResponseBody
import retrofit2.Call
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealFileDownloadManager @Inject constructor() : FileDownloadManager {
    private val callsMap = ConcurrentHashMap<Long, Call<ResponseBody>>()

    /**
     * Call this method to cancel and remove an ongoing download, or to clean up the [downloadId] from the list of downloads.
     * It is safe to call this method multiple times with the same [downloadId].
     */
    override fun remove(downloadId: Long) {
        Timber.d("Removing download $downloadId")
        callsMap[downloadId]?.cancel()
        callsMap.remove(downloadId)
    }

    /**
     * Call this method to add the ongoing download to the list of downloads.
     * The ongoing download is identified by its [downloadId] and the network [call].
     */
    fun add(downloadId: Long, call: Call<ResponseBody>) {
        Timber.d("Adding download $downloadId")
        callsMap[downloadId] = call
    }
}
