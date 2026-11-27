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
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesWorker(AppScope::class)
internal class LanguagePackDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var feature: PageTranslationFeature

    @Inject
    lateinit var store: ModelsManifestStore

    @Inject
    lateinit var fetcher: Lazy<LanguagePackFetcher>

    @Inject
    lateinit var installer: Lazy<LanguagePackInstaller>

    @Inject
    lateinit var directories: LanguagePackDirectories

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        val pair = inputData.getString(PAIR_KEY) ?: return@withContext failure(Reason.DOWNLOAD_ERROR)
        // a deleted pack can only be restored until the pair's next operation starts
        directories.getOldDirectory(pair).deleteRecursively()
        directories.getStagingDirectory(pair).deleteRecursively()
        if (directories.getPackDirectory(pair).exists()) return@withContext Result.success()

        try {
            download(pair)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(ERROR) { "Page translation language pack download failed: ${e.asLog()}" }
            failure(Reason.DOWNLOAD_ERROR)
        }
    }

    private suspend fun download(pair: String): Result {
        if (!feature.translation().isEnabled()) return failure(Reason.DOWNLOAD_ERROR)

        val manifest = store.readManifest()
        val model = manifest?.models?.get(pair) ?: return failure(Reason.DOWNLOAD_ERROR)

        // WorkManager keeps the progress of a stopped attempt, but the fetcher starts over
        setProgress(workDataOf(DOWNLOADED_BYTES_KEY to 0L, TOTAL_BYTES_KEY to model.totalBytes))

        val result = coroutineScope {
            val fetch = async {
                val stagingDirectory = directories.getStagingDirectory(pair)
                val fetchResult = fetcher.value.fetch(
                    baseUrl = manifest.baseUrl,
                    model = model,
                    stagingDirectory = stagingDirectory,
                ) { downloadedBytes ->
                    setProgress(workDataOf(DOWNLOADED_BYTES_KEY to downloadedBytes, TOTAL_BYTES_KEY to model.totalBytes))
                }
                if (fetchResult != SUCCESS) return@async fetchResult

                if (installer.value.install(pair, model, stagingDirectory, directories.getPackDirectory(pair))) SUCCESS else FAILURE
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

        return when (result) {
            SUCCESS -> Result.success()
            NOT_ENOUGH_STORAGE -> failure(Reason.NOT_ENOUGH_STORAGE)
            FAILURE -> failure(Reason.DOWNLOAD_ERROR)
        }
    }

    // WorkManager fails every operation queued after a failed work, so a failed download still succeeds
    private fun failure(reason: Reason) = Result.success(workDataOf(FAILURE_REASON_KEY to reason.name))

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

        private const val NOTIFICATION_CHANNEL_ID = "com.duckduckgo.pagetranslation.downloads"
        private const val NOTIFICATION_ID = 7_341
    }
}
