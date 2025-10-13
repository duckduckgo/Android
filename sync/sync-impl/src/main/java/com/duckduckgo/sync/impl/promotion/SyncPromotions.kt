/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.promotion

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Used for determining if a sync promotion should be shown to the user and for recording when a promotion has been dismissed.
 */
interface SyncPromotions {

    /**
     * Returns true if the bookmarks promotion should be shown to the user.
     * @param savedBookmarks The number of bookmarks saved by the user.
     */
    suspend fun canShowBookmarksPromotion(savedBookmarks: Int): Boolean

    /**
     * Records that the bookmarks promotion has been dismissed.
     */
    suspend fun recordBookmarksPromotionDismissed()

    /**
     * Returns true if the passwords promotion should be shown to the user.
     */
    suspend fun canShowPasswordsPromotion(savedPasswords: Int): Boolean

    /**
     * Records that the passwords promotion has been dismissed.
     */
    suspend fun recordPasswordsPromotionDismissed()
}

@ContributesBinding(AppScope::class)
class SyncPromotionsImpl @Inject constructor(
    private val syncState: DeviceSyncState,
    private val syncPromotionFeature: SyncPromotionFeature,
    private val dispatchers: DispatcherProvider,
    private val dataStore: SyncPromotionDataStore,
) : SyncPromotions {

    override suspend fun canShowBookmarksPromotion(savedBookmarks: Int): Boolean {
        if (savedBookmarks == 0) return false

        return withContext(dispatchers.io()) {
            if (!isSyncFeatureEnabled() || isUserSyncingAlready()) return@withContext false
            if (!isBookmarksPromoEnabled()) return@withContext false

            if (dataStore.hasBookmarksPromoBeenDismissed()) return@withContext false

            true
        }
    }

    override suspend fun recordBookmarksPromotionDismissed() {
        withContext(dispatchers.io()) {
            dataStore.recordBookmarksPromoDismissed()
        }
    }

    override suspend fun canShowPasswordsPromotion(savedPasswords: Int): Boolean {
        if (savedPasswords == 0) return false

        return withContext(dispatchers.io()) {
            if (!isSyncFeatureEnabled() || isUserSyncingAlready()) return@withContext false
            if (!isPasswordsPromoEnabled()) return@withContext false

            if (dataStore.hasPasswordsPromoBeenDismissed()) return@withContext false

            true
        }
    }

    override suspend fun recordPasswordsPromotionDismissed() {
        withContext(dispatchers.io()) {
            dataStore.recordPasswordsPromoDismissed()
        }
    }

    private fun isSyncFeatureEnabled() = syncState.isFeatureEnabled()
    private fun isUserSyncingAlready() = syncState.isUserSignedInOnDevice()
    private fun isBookmarksPromoEnabled() = syncPromotionFeature.bookmarks().isEnabled() && syncPromotionFeature.self().isEnabled()
    private fun isPasswordsPromoEnabled() = syncPromotionFeature.passwords().isEnabled() && syncPromotionFeature.self().isEnabled()
}
