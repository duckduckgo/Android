/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.sync

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.FeatureSyncError
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.FeatureSyncError.INVALID_REQUEST
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.INFO
import logcat.logcat

interface SavedSitesSyncFeatureListener {
    fun onSuccess(changes: SyncChangesResponse)
    fun onError(syncError: FeatureSyncError)
    fun onSyncDisabled()
}

@ContributesBinding(AppScope::class)
class AppSavedSitesSyncFeatureListener @Inject constructor(
    private val context: Context,
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val notificationManager: NotificationManagerCompat,
    private val notificationBuilder: SavedSitesSyncNotificationBuilder,
) : SavedSitesSyncFeatureListener {

    override fun onSuccess(changes: SyncChangesResponse) {
        if (changes.jsonString.isEmpty()) return // no changes, skip

        if (savedSitesSyncStore.isSyncPaused) {
            savedSitesSyncStore.isSyncPaused = false
            savedSitesSyncStore.syncPausedReason = ""
            cancelNotification()
        }
    }

    override fun onError(syncError: FeatureSyncError) {
        logcat { "Sync-Bookmarks: $syncError received, current state isPaused:${savedSitesSyncStore.isSyncPaused}" }
        when (syncError) {
            COLLECTION_LIMIT_REACHED,
            INVALID_REQUEST,
            -> {
                if (!savedSitesSyncStore.isSyncPaused || savedSitesSyncStore.syncPausedReason != syncError.name) {
                    logcat(INFO) { "Sync-Bookmarks: should trigger notification for $syncError" }
                    triggerNotification(syncError)
                }
                savedSitesSyncStore.isSyncPaused = true
                savedSitesSyncStore.syncPausedReason = syncError.name
            }
        }
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.isSyncPaused = false
        savedSitesSyncStore.syncPausedReason = ""
        cancelNotification()
    }

    private fun triggerNotification(syncError: FeatureSyncError) {
        val notification = when (syncError) {
            COLLECTION_LIMIT_REACHED -> notificationBuilder.buildRateLimitNotification(context)
            INVALID_REQUEST -> notificationBuilder.buildInvalidRequestNotification(context)
        }
        notificationManager.checkPermissionAndNotify(
            context,
            SYNC_PAUSED_SAVED_SITES_NOTIFICATION_ID,
            notification,
        )
    }

    private fun cancelNotification() {
        notificationManager.cancel(SYNC_PAUSED_SAVED_SITES_NOTIFICATION_ID)
    }

    companion object {
        private const val SYNC_PAUSED_SAVED_SITES_NOTIFICATION_ID = 5451
    }
}
