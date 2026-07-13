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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.di.SyncPromotion
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarkAddedDialog
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.BookmarksScreen
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.ChatTabPage
import com.duckduckgo.sync.impl.promotion.SyncPromotionDataStore.PromotionType.PasswordsScreen
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SyncPromotionDataStore {
    suspend fun hasPromoBeenDismissed(promotionType: PromotionType): Boolean
    suspend fun recordPromoDismissed(promotionType: PromotionType)
    suspend fun getPromoImpressionCount(promotionType: PromotionType): Long
    suspend fun recordPromoImpression(promotionType: PromotionType)
    suspend fun clearPromoHistory(promotionType: PromotionType)

    sealed interface PromotionType {
        object BookmarksScreen : PromotionType
        object PasswordsScreen : PromotionType
        object BookmarkAddedDialog : PromotionType
        object ChatTabPage : PromotionType
    }
}

@ContributesBinding(AppScope::class)
class SyncPromotionDataStoreImpl @Inject constructor(
    @SyncPromotion private val dataStore: DataStore<Preferences>,
) : SyncPromotionDataStore {

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
        dataStore.edit { it[key] = (it[key] ?: 0) + 1 }
    }

    override suspend fun clearPromoHistory(promotionType: PromotionType) {
        val dismissKey = promotionType.dismissKey()
        val impressionKey = promotionType.impressionsKey()
        dataStore.edit {
            it.remove(dismissKey)
            it.remove(impressionKey)
        }
    }

    private fun PromotionType.dismissKey(): Preferences.Key<Long> {
        return when (this) {
            BookmarkAddedDialog -> bookmarkAddedDialogPromoDismissedKey
            BookmarksScreen -> bookmarksScreenPromoDismissedKey
            PasswordsScreen -> passwordsPromoDismissedKey
            ChatTabPage -> chatTabPagePromoDismissedKey
        }
    }

    private fun PromotionType.impressionsKey(): Preferences.Key<Long> {
        return when (this) {
            BookmarkAddedDialog -> bookmarkAddedDialogPromoImpressionsKey
            BookmarksScreen -> bookmarksScreenPromoImpressionsKey
            PasswordsScreen -> passwordsPromoImpressionsKey
            ChatTabPage -> chatTabPagePromoImpressionsKey
        }
    }

    companion object {
        private val bookmarksScreenPromoDismissedKey = longPreferencesKey("bookmarks_promo_dismissed")
        private val bookmarksScreenPromoImpressionsKey = longPreferencesKey("bookmarks_promo_impressions")
        private val bookmarkAddedDialogPromoDismissedKey = longPreferencesKey("bookmark_added_dialog_promo_dismissed")
        private val bookmarkAddedDialogPromoImpressionsKey = longPreferencesKey("bookmark_added_dialog_promo_impressions")
        private val passwordsPromoDismissedKey = longPreferencesKey("passwords_promo_dismissed")
        private val passwordsPromoImpressionsKey = longPreferencesKey("passwords_promo_impressions")
        private val chatTabPagePromoDismissedKey = longPreferencesKey("chat_tab_page_promo_dismissed")
        private val chatTabPagePromoImpressionsKey = longPreferencesKey("chat_tab_page_promo_impressions")
    }
}
