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

package com.duckduckgo.subscriptions.impl.sync

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.store.AuthDataStore
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class)
class SubscriptionsSyncableSetting @Inject constructor(
    private val syncSettingsListener: SyncSettingsListener,
    private val authDataStore: AuthDataStore,
) : SyncableSetting {

    override val key: String = SUBSCRIPTION_SETTING

    private var listener: () -> Unit = {}

    override fun getValue(): String? {
        return authDataStore.accessToken
    }

    override fun save(value: String?): Boolean {
        val current = authDataStore.accessToken
        value?.let {
            authDataStore.accessToken = it
            if (current.isNullOrBlank()) {
                listener.invoke()
            }
        }
        return true
    }

    override fun deduplicate(value: String?): Boolean {
        val current = authDataStore.accessToken
        value?.let {
            authDataStore.accessToken = it
            if (current.isNullOrBlank()) {
                listener.invoke()
            }
        }
        return true
    }

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) {
        this.listener = onDataChanged
    }

    override fun onSettingChanged() {
        syncSettingsListener.onSettingChanged(key)
    }

    companion object {
        const val SUBSCRIPTION_SETTING = "subscription_access_token"
    }
}
