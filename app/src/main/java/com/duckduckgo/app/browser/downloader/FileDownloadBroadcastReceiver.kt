/*
 * Copyright (c) 2021 DuckDuckGo
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
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FileDownloadBroadcastReceiver @Inject constructor(
    private val context: Context,
    private val pixel: Pixel,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcher: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : BroadcastReceiver(), DefaultLifecycleObserver {

    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {

        appCoroutineScope.launch(dispatcher.io()) {
            Timber.d("Download completed.")
            val downloadManager = context?.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?

            val query = DownloadManager.Query()
            query.setFilterByStatus(DownloadManager.STATUS_FAILED or DownloadManager.STATUS_SUCCESSFUL)

            val cursor = downloadManager?.query(query)
            if (cursor?.moveToFirst() == true) {
                val index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                when (cursor.getInt(index)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        Timber.d("Download completed with success.")
                        pixel.fire(
                            AppPixelName.DOWNLOAD_REQUEST_SUCCEEDED,
                            parameters = mapOf(PixelParameter.OS_VERSION to appBuildConfig.sdkInt.toString())
                        )
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Timber.d("Download completed, but failed.")
                        pixel.fire(
                            AppPixelName.DOWNLOAD_REQUEST_FAILED,
                            parameters = mapOf(PixelParameter.OS_VERSION to appBuildConfig.sdkInt.toString())
                        )
                    }
                }
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        context.registerReceiver(this, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        context.unregisterReceiver(this)
    }
}
