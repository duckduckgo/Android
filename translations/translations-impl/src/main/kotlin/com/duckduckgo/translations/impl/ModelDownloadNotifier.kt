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

package com.duckduckgo.translations.impl

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.translations.api.ModelDownloadStatus
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.Locale
import javax.inject.Inject

/** Surfaces model-download progress and the final result as a status-bar notification. */
interface ModelDownloadNotifier {
    fun update(sourceLanguage: String, targetLanguage: String, status: ModelDownloadStatus)
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealModelDownloadNotifier @Inject constructor(
    private val context: Context,
    private val notificationManager: NotificationManagerCompat,
) : ModelDownloadNotifier {

    override fun update(sourceLanguage: String, targetLanguage: String, status: ModelDownloadStatus) {
        ensureChannel()
        val pair = context.getString(R.string.translationModelPair, sourceLanguage.displayName(), targetLanguage.displayName())
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentText(pair)

        val notification = when (status) {
            is ModelDownloadStatus.InProgress ->
                builder
                    .setContentTitle(context.getString(R.string.translationModelDownloadingTitle))
                    .setOngoing(true)
                    .setProgress(100, status.percent ?: 0, status.percent == null)
                    .build()
            ModelDownloadStatus.Completed ->
                builder
                    .setContentTitle(context.getString(R.string.translationModelDownloadedTitle))
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()
            is ModelDownloadStatus.Failed ->
                builder
                    .setContentTitle(context.getString(R.string.translationModelDownloadFailedTitle))
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()
        }
        notificationManager.checkPermissionAndNotify(context, NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.translationModelNotificationChannel),
            NotificationManager.IMPORTANCE_LOW,
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun String.displayName(): String = Locale.forLanguageTag(this).displayLanguage.ifBlank { this }

    companion object {
        private const val CHANNEL_ID = "com.duckduckgo.translations.modelDownload"
        private const val NOTIFICATION_ID = 9213
    }
}
