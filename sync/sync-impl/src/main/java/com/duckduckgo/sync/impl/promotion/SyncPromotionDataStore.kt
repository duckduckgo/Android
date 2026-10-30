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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.di.SyncPromotion
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksImport
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreenMultiDevice
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.ChatTabPage
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsImport
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface SyncPromotionDataStore {
    suspend fun hasPromoBeenDismissed(promotionType: PromotionType): Boolean
    suspend fun recordPromoDismissed(promotionType: PromotionType)
    suspend fun getPromoImpressionCount(promotionType: PromotionType): Long
    suspend fun recordPromoImpression(promotionType: PromotionType)
    suspend fun getTimeSinceLastPromoImpression(promotionType: PromotionType): Duration?
    suspend fun clearPromoHistory(promotionType: PromotionType)

    enum class PromotionType {
        BookmarksScreen,
        BookmarksScreenMultiDevice,
        PasswordsScreen,
        BookmarksImport,
        PasswordsImport,
        BookmarkAddedDialog,
        ChatTabPage,
    }
}

/**
 * Additional operations for internal builds, used by the promotion debug screen.
 */
interface InternalSyncPromotionDataStore : SyncPromotionDataStore {
    suspend fun getPromoDismissTime(promotionType: PromotionType): Instant?
    suspend fun setPromoDismissTime(promotionType: PromotionType, time: Instant)
    suspend fun clearPromoDismissal(promotionType: PromotionType)
    suspend fun getLastPromoImpressionTime(promotionType: PromotionType): Instant?
    suspend fun setLastPromoImpressionTime(promotionType: PromotionType, time: Instant)
    suspend fun clearPromoImpressions(promotionType: PromotionType)
    suspend fun clearAllPromoHistory()
}

@ContributesBinding(AppScope::class, binding = binding<SyncPromotionDataStore>())
@ContributesBinding(AppScope::class, binding = binding<InternalSyncPromotionDataStore>())
class SyncPromotionDataStoreImpl @Inject constructor(
    @SyncPromotion private val dataStore: DataStore<Preferences>,
    private val currentTimeProvider: CurrentTimeProvider,
) : InternalSyncPromotionDataStore {

    override suspend fun hasPromoBeenDismissed(promotionType: PromotionType): Boolean {
        val key = promotionType.dismissKey()
        return dataStore.data.map { it[key] }.firstOrNull() != null
    }

    override suspend fun recordPromoDismissed(promotionType: PromotionType) {
        val key = promotionType.dismissKey()
        dataStore.edit { it[key] = System.currentTimeMillis() }
    }

    override suspend fun getPromoImpressionCount(promotionType: PromotionType): Long {
        val key = promotionType.impressionsKey()
        return dataStore.data.map { it[key] }.firstOrNull() ?: 0
    }

    override suspend fun recordPromoImpression(promotionType: PromotionType) {
        val key = promotionType.impressionsKey()
        val lastImpressionKey = promotionType.lastImpressionKey()
        dataStore.edit {
            it[key] = (it[key] ?: 0) + 1
            it[lastImpressionKey] = currentTimeProvider.currentTimeMillis()
        }
    }

    override suspend fun getTimeSinceLastPromoImpression(promotionType: PromotionType): Duration? {
        val key = promotionType.lastImpressionKey()
        val lastImpressionMillis = dataStore.data.map { it[key] }.firstOrNull() ?: return null
        return (currentTimeProvider.currentTimeMillis() - lastImpressionMillis).milliseconds
    }

    override suspend fun clearPromoHistory(promotionType: PromotionType) {
        val dismissKey = promotionType.dismissKey()
        val impressionKey = promotionType.impressionsKey()
        val lastImpressionKey = promotionType.lastImpressionKey()
        dataStore.edit {
            it.remove(dismissKey)
            it.remove(impressionKey)
            it.remove(lastImpressionKey)
        }
    }

    override suspend fun getPromoDismissTime(promotionType: PromotionType): Instant? {
        val key = promotionType.dismissKey()
        return dataStore.data.map { it[key] }.firstOrNull()?.let(Instant::ofEpochMilli)
    }

    override suspend fun setPromoDismissTime(promotionType: PromotionType, time: Instant) {
        val key = promotionType.dismissKey()
        dataStore.edit { it[key] = time.toEpochMilli() }
    }

    override suspend fun clearPromoDismissal(promotionType: PromotionType) {
        val key = promotionType.dismissKey()
        dataStore.edit { it.remove(key) }
    }

    override suspend fun getLastPromoImpressionTime(promotionType: PromotionType): Instant? {
        val key = promotionType.lastImpressionKey()
        return dataStore.data.map { it[key] }.firstOrNull()?.let(Instant::ofEpochMilli)
    }

    override suspend fun setLastPromoImpressionTime(promotionType: PromotionType, time: Instant) {
        val key = promotionType.lastImpressionKey()
        dataStore.edit { it[key] = time.toEpochMilli() }
    }

    override suspend fun clearPromoImpressions(promotionType: PromotionType) {
        val impressionKey = promotionType.impressionsKey()
        val lastImpressionKey = promotionType.lastImpressionKey()
        dataStore.edit {
            it.remove(impressionKey)
            it.remove(lastImpressionKey)
        }
    }

    override suspend fun clearAllPromoHistory() {
        dataStore.edit { it.clear() }
    }

    private fun PromotionType.dismissKey(): Preferences.Key<Long> {
        return when (this) {
            BookmarkAddedDialog -> bookmarkAddedDialogPromoDismissedKey
            BookmarksScreen -> bookmarksScreenPromoDismissedKey
            BookmarksScreenMultiDevice -> bookmarksScreenMultiDevicePromoDismissedKey
            PasswordsScreen -> passwordsPromoDismissedKey
            ChatTabPage -> chatTabPagePromoDismissedKey
            BookmarksImport -> bookmarksImportPromoDismissedKey
            PasswordsImport -> passwordsImportPromoDismissedKey
        }
    }

    private fun PromotionType.impressionsKey(): Preferences.Key<Long> {
        return when (this) {
            BookmarkAddedDialog -> bookmarkAddedDialogPromoImpressionsKey
            BookmarksScreen -> bookmarksScreenPromoImpressionsKey
            BookmarksScreenMultiDevice -> bookmarksScreenMultiDevicePromoImpressionsKey
            PasswordsScreen -> passwordsPromoImpressionsKey
            ChatTabPage -> chatTabPagePromoImpressionsKey
            BookmarksImport -> bookmarksImportPromoImpressionsKey
            PasswordsImport -> passwordsImportPromoImpressionsKey
        }
    }

    private fun PromotionType.lastImpressionKey(): Preferences.Key<Long> {
        return when (this) {
            BookmarkAddedDialog -> bookmarkAddedDialogPromoLastImpressionKey
            BookmarksScreen -> bookmarksScreenPromoLastImpressionKey
            BookmarksScreenMultiDevice -> bookmarksScreenMultiDevicePromoLastImpressionKey
            PasswordsScreen -> passwordsPromoLastImpressionKey
            ChatTabPage -> chatTabPagePromoLastImpressionKey
            BookmarksImport -> bookmarksImportPromoLastImpressionKey
            PasswordsImport -> passwordsImportPromoLastImpressionKey
        }
    }

    companion object {
        private val bookmarksScreenPromoDismissedKey = longPreferencesKey("bookmarks_promo_dismissed")
        private val bookmarksScreenPromoImpressionsKey = longPreferencesKey("bookmarks_promo_impressions")
        private val bookmarksScreenPromoLastImpressionKey = longPreferencesKey("bookmarks_promo_last_impression")
        private val bookmarksScreenMultiDevicePromoDismissedKey = longPreferencesKey("bookmarks_multi_device_promo_dismissed")
        private val bookmarksScreenMultiDevicePromoImpressionsKey = longPreferencesKey("bookmarks_multi_device_promo_impressions")
        private val bookmarksScreenMultiDevicePromoLastImpressionKey = longPreferencesKey("bookmarks_multi_device_promo_last_impression")
        private val bookmarkAddedDialogPromoDismissedKey = longPreferencesKey("bookmark_added_dialog_promo_dismissed")
        private val bookmarkAddedDialogPromoImpressionsKey = longPreferencesKey("bookmark_added_dialog_promo_impressions")
        private val bookmarkAddedDialogPromoLastImpressionKey = longPreferencesKey("bookmark_added_dialog_promo_last_impression")
        private val passwordsPromoDismissedKey = longPreferencesKey("passwords_promo_dismissed")
        private val passwordsPromoImpressionsKey = longPreferencesKey("passwords_promo_impressions")
        private val passwordsPromoLastImpressionKey = longPreferencesKey("passwords_promo_last_impression")
        private val chatTabPagePromoDismissedKey = longPreferencesKey("chat_tab_page_promo_dismissed")
        private val chatTabPagePromoImpressionsKey = longPreferencesKey("chat_tab_page_promo_impressions")
        private val chatTabPagePromoLastImpressionKey = longPreferencesKey("chat_tab_page_promo_last_impression")
        private val bookmarksImportPromoDismissedKey = longPreferencesKey("bookmarks_import_promo_dismissed")
        private val bookmarksImportPromoImpressionsKey = longPreferencesKey("bookmarks_import_promo_impressions")
        private val bookmarksImportPromoLastImpressionKey = longPreferencesKey("bookmarks_import_promo_last_impression")
        private val passwordsImportPromoDismissedKey = longPreferencesKey("passwords_import_promo_dismissed")
        private val passwordsImportPromoImpressionsKey = longPreferencesKey("passwords_import_promo_impressions")
        private val passwordsImportPromoLastImpressionKey = longPreferencesKey("passwords_import_promo_last_impression")
    }
}
