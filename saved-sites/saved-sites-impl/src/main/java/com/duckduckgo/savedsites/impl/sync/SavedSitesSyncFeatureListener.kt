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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.FeatureSyncError
import com.duckduckgo.sync.api.engine.FeatureSyncError.COLLECTION_LIMIT_REACHED
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

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
            cancelNotification()
        }
    }

    override fun onError(syncError: FeatureSyncError) {
        when (syncError) {
            COLLECTION_LIMIT_REACHED -> {
                if (!savedSitesSyncStore.isSyncPaused) {
                    triggerNotification()
                }
                savedSitesSyncStore.isSyncPaused = true
            }
        }
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.isSyncPaused = false
        cancelNotification()
    }

    private fun triggerNotification() {
        notificationManager.notify(
            666,
            notificationBuilder.buildRateLimitNotification(context),
        )
    }

    private fun cancelNotification() {
        notificationManager.cancel(666)
    }
}
