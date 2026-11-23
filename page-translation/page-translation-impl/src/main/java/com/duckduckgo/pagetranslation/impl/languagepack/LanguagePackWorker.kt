/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl.languagepack

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.R
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadState.Failed.Reason
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult.FAILURE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult.NOT_ENOUGH_STORAGE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult.SUCCESS
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesWorker(AppScope::class)
internal class LanguagePackWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var feature: PageTranslationFeature

    @Inject
    lateinit var deviceSupport: PageTranslationDeviceSupport

    @Inject
    lateinit var store: ModelsManifestStore

    @Inject
    lateinit var fetcher: Lazy<LanguagePackFetcher>

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        val pair = inputData.getString(PAIR_KEY)
        if (pair == null || !feature.translation().isEnabled() || !deviceSupport.isSupported()) return@withContext Result.failure()

        val manifest = store.readManifest()
        val model = manifest?.models?.get(pair) ?: return@withContext Result.failure()

        val result = coroutineScope {
            val fetch = async {
                fetcher.value.fetch(
                    baseUrl = manifest.baseUrl,
                    model = model,
                    stagingDirectory = getStagingDirectory(applicationContext, pair),
                ) { downloadedBytes ->
                    setProgress(workDataOf(DOWNLOADED_BYTES_KEY to downloadedBytes, TOTAL_BYTES_KEY to model.totalBytes))
                }
            }
            val stopWhenDisabled = launch {
                feature.translation().enabled().firstOrNull { isEnabled -> !isEnabled } ?: return@launch
                fetch.cancel()
            }

            try {
                fetch.await()
            } catch (_: CancellationException) {
                // rethrows when the system stopped the job, otherwise the feature was turned off
                ensureActive()
                FAILURE
            } finally {
                stopWhenDisabled.cancel()
            }
        }

        when (result) {
            SUCCESS -> Result.success()
            NOT_ENOUGH_STORAGE -> Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.NOT_ENOUGH_STORAGE.name))
            FAILURE -> Result.failure(workDataOf(FAILURE_REASON_KEY to Reason.DOWNLOAD_ERROR.name))
        }
    }

    // only called before Android 12, where expedited work runs as a foreground service
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        notificationManager.createNotificationChannel(
            NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(applicationContext.getString(R.string.pageTranslationDownloadNotificationChannel))
                .build(),
        )

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(CommonR.drawable.notification_logo)
            .setContentTitle(applicationContext.getString(R.string.pageTranslationDownloadNotificationTitle))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val PAIR_KEY = "pair"
        const val DOWNLOADED_BYTES_KEY = "downloadedBytes"
        const val TOTAL_BYTES_KEY = "totalBytes"
        const val FAILURE_REASON_KEY = "failureReason"

        private const val PACKS_DIRECTORY = "page-translation/packs"
        private const val STAGING_SUFFIX = ".tmp"
        private const val NOTIFICATION_CHANNEL_ID = "com.duckduckgo.pagetranslation.downloads"
        private const val NOTIFICATION_ID = 7_341

        fun getStagingDirectory(context: Context, pair: String): File = context.filesDir.resolve(PACKS_DIRECTORY).resolve(pair + STAGING_SUFFIX)
    }
}
