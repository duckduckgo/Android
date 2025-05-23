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

package com.duckduckgo.app.email.sync

import com.duckduckgo.app.email.db.*
import com.duckduckgo.app.email.sync.Adapters.Companion.adapter
import com.duckduckgo.app.pixels.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.settings.api.SyncSettingsListener
import com.duckduckgo.sync.settings.api.SyncableSetting
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.*
import javax.inject.*
import logcat.LogPriority.INFO
import logcat.logcat

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(scope = AppScope::class, boundType = SyncableSetting::class)
class EmailSync @Inject constructor(
    private val emailDataStore: EmailDataStore,
    private val syncSettingsListener: SyncSettingsListener,
    private val pixel: Pixel,
) : SyncableSetting {

    private var listener: () -> Unit = {}

    override val key: String = DUCK_EMAIL_SETTING

    override fun getValue(): String? {
        val address = emailDataStore.emailUsername ?: return null
        val token = emailDataStore.emailToken ?: return null
        DuckAddressSetting(
            username = address,
            personal_access_token = token,
        ).let {
            return adapter.toJson(it)
        }
    }

    override fun save(value: String?): Boolean {
        logcat(INFO) { "Sync-Settings: save($value)" }
        val duckAddressSetting = runCatching { adapter.fromJson(value) }.getOrNull()
        if (duckAddressSetting != null) {
            val duckAddress = duckAddressSetting.username
            val personalAccessToken = duckAddressSetting.personal_access_token
            storeNewCredentials(duckAddress, personalAccessToken)
            return true
        } else {
            storeNewCredentials("", "")
            return true
        }
    }

    override fun deduplicate(value: String?): Boolean {
        logcat(INFO) { "Sync-Settings: mergeRemote($value)" }
        val duckAddressSetting = runCatching { adapter.fromJson(value) }.getOrNull()
        if (duckAddressSetting != null) {
            val duckUsername = duckAddressSetting.username
            val personalAccessToken = duckAddressSetting.personal_access_token
            if (!emailDataStore.emailToken.isNullOrBlank() && !emailDataStore.emailUsername.isNullOrBlank()) {
                if (duckUsername != emailDataStore.emailUsername) {
                    storeNewCredentials(duckUsername, personalAccessToken)
                    pixel.fire(AppPixelName.DUCK_EMAIL_OVERRIDE_PIXEL)
                    return true
                }
            } else {
                storeNewCredentials(duckUsername, personalAccessToken)
                return true
            }
        }
        return false
    }

    private fun storeNewCredentials(username: String, token: String) {
        logcat(INFO) { "Sync-Settings: storeNewCredentials($username, $token)" }
        emailDataStore.emailToken = token
        emailDataStore.emailUsername = username
        listener.invoke()
    }

    override fun registerToRemoteChanges(onDataChanged: () -> Unit) {
        this.listener = onDataChanged
    }

    override fun onSettingChanged() {
        syncSettingsListener.onSettingChanged(key)
    }

    override fun onSyncDisabled() {
        // no-op
    }

    companion object {
        const val DUCK_EMAIL_SETTING = "email_protection_generation"
    }
}

class DuckAddressSetting(
    val username: String,
    val personal_access_token: String,
)

private class Adapters {
    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<DuckAddressSetting> = moshi.adapter(DuckAddressSetting::class.java).lenient()
    }
}
