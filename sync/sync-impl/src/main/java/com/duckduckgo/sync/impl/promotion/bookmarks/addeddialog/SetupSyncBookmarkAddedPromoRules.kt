/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.sync.impl.promotion.bookmarks.addeddialog

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.SyncPromotionFeature
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

interface SetupSyncBookmarkAddedPromoRules {
    suspend fun canShowPromo(): Boolean
}

@ContributesBinding(AppScope::class)
class RealSetupSyncBookmarkAddedPromoRules @Inject constructor(
    private val syncState: DeviceSyncState,
    private val dispatchers: DispatcherProvider,
    private val syncPromotionDataStore: SyncPromotionDataStore,
    private val syncPromotionFeature: SyncPromotionFeature,
) : SetupSyncBookmarkAddedPromoRules {

    override suspend fun canShowPromo(): Boolean {
        return withContext(dispatchers.io()) {
            if (!isPromoFeatureEnabled()) {
                return@withContext false
            }

            if (!isSyncFeatureEnabled()) {
                return@withContext false
            }

            if (isUserSyncingAlready()) {
                return@withContext false
            }

            if (syncPromotionDataStore.hasPromoBeenDismissed(PromotionType.BookmarkAddedDialog)) {
                return@withContext false
            }

            true
        }.also {
            logcat { "Sync-promo: determined if canShowPromo for ${javaClass.simpleName}: $it" }
        }
    }
    private fun isPromoFeatureEnabled() = syncPromotionFeature.bookmarkAddedDialog().isEnabled() && syncPromotionFeature.self().isEnabled()
    private fun isSyncFeatureEnabled() = syncState.isFeatureEnabled()
    private fun isUserSyncingAlready() = syncState.isUserSignedInOnDevice()
}
