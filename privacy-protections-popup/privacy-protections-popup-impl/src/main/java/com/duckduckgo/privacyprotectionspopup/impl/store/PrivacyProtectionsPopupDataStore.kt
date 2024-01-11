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

package com.duckduckgo.privacyprotectionspopup.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStoreImpl.Keys.DO_NOT_SHOW_AGAIN_CLICKED
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStoreImpl.Keys.POPUP_TRIGGER_COUNT
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStoreImpl.Keys.TOGGLE_USAGE_TIMESTAMP
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface PrivacyProtectionsPopupDataStore {
    val data: Flow<PrivacyProtectionsPopupData>

    suspend fun getToggleUsageTimestamp(): Instant?
    suspend fun setToggleUsageTimestamp(timestamp: Instant)
    suspend fun getPopupTriggerCount(): Int
    suspend fun setPopupTriggerCount(count: Int)
    suspend fun getDoNotShowAgainClicked(): Boolean
    suspend fun setDoNotShowAgainClicked(clicked: Boolean)
}

data class PrivacyProtectionsPopupData(
    val toggleUsedAt: Instant?,
    val popupTriggerCount: Int,
    val doNotShowAgainClicked: Boolean,
)

@ContributesBinding(AppScope::class)
class PrivacyProtectionsPopupDataStoreImpl @Inject constructor(
    @PrivacyProtectionsPopup private val store: DataStore<Preferences>,
) : PrivacyProtectionsPopupDataStore {

    override val data: Flow<PrivacyProtectionsPopupData>
        get() = store.data
            .map { prefs ->
                PrivacyProtectionsPopupData(
                    toggleUsedAt = prefs[TOGGLE_USAGE_TIMESTAMP]?.let { Instant.ofEpochMilli(it) },
                    popupTriggerCount = prefs[POPUP_TRIGGER_COUNT] ?: 0,
                    doNotShowAgainClicked = prefs[DO_NOT_SHOW_AGAIN_CLICKED] == true,
                )
            }
            .distinctUntilChanged()

    override suspend fun getToggleUsageTimestamp(): Instant? =
        data.first().toggleUsedAt

    override suspend fun setToggleUsageTimestamp(timestamp: Instant) {
        store.edit { prefs -> prefs[TOGGLE_USAGE_TIMESTAMP] = timestamp.toEpochMilli() }
    }

    override suspend fun getPopupTriggerCount(): Int =
        data.first().popupTriggerCount

    override suspend fun setPopupTriggerCount(count: Int) {
        store.edit { prefs -> prefs[POPUP_TRIGGER_COUNT] = count }
    }

    override suspend fun getDoNotShowAgainClicked(): Boolean =
        data.first().doNotShowAgainClicked

    override suspend fun setDoNotShowAgainClicked(clicked: Boolean) {
        store.edit { prefs -> prefs[DO_NOT_SHOW_AGAIN_CLICKED] = clicked }
    }

    private object Keys {
        val TOGGLE_USAGE_TIMESTAMP = longPreferencesKey(name = "toggle_usage_timestamp")
        val POPUP_TRIGGER_COUNT = intPreferencesKey(name = "popup_trigger_count")
        val DO_NOT_SHOW_AGAIN_CLICKED = booleanPreferencesKey(name = "dont_show_again_clicked")
    }
}
