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
import com.duckduckgo.di.scopes.*
import com.duckduckgo.settings.api.*
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.*
import timber.log.*
import javax.inject.*

@SingleInstanceIn(AppScope::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncableSetting::class)
@ContributesMultibinding(scope = AppScope::class, boundType = SyncableSetting::class)
class EmailSync @Inject constructor(
    private val emailDataStore: EmailDataStore,
) : SyncableSetting {

    private var listener: (String) -> Unit = {}

    override val key: String = DUCK_EMAIL_SETTING

    override fun getValue(): String? {
        val address = emailDataStore.emailUsername ?: return null
        val token = emailDataStore.emailToken ?: return null
        DuckAddressSetting(
            main_duck_address = address,
            personal_access_token = token,
        ).let {
            return adapter.toJson(it)
        }
    }

    override fun save(value: String?): Boolean {
        Timber.i("Sync-Settings: save($value)")
        val duckAddressSetting = runCatching { adapter.fromJson(value) }.getOrNull()
        if (duckAddressSetting!=null) {
            val duckAddress = duckAddressSetting.main_duck_address
            val personalAccessToken = duckAddressSetting.personal_access_token
            storeNewCredentials(duckAddress, personalAccessToken)
            return true
        } else {
            storeNewCredentials("", "")
            return true
        }
    }

    override fun mergeRemote(value: String?): Boolean {
        Timber.i("Sync-Settings: mergeRemote($value)")
        val duckAddressSetting = runCatching { adapter.fromJson(value) }.getOrNull()
        if (duckAddressSetting!=null) {
            val duckAddress = duckAddressSetting.main_duck_address
            val personalAccessToken = duckAddressSetting.personal_access_token
            if (!emailDataStore.emailToken.isNullOrBlank() && !emailDataStore.emailUsername.isNullOrBlank()) {
                if (duckAddress!=emailDataStore.emailUsername) {
                    storeNewCredentials(duckAddress, personalAccessToken)
                    return true
                }
            } else {
                storeNewCredentials(duckAddress, personalAccessToken)
                return true
            }
        }
        return false
    }

    private fun storeNewCredentials(address: String, token: String) {
        Timber.i("Sync-Settings: storeNewCredentials($address, $token)")
        emailDataStore.emailToken = token
        emailDataStore.emailUsername = address
        listener.invoke(address)
    }

    override fun registerToRemoteChanges(listener: (String) -> Unit) {
        this.listener = listener
    }

    companion object {
        const val DUCK_EMAIL_SETTING = "email_protection_generation"
    }
}

class DuckAddressSetting(
    val main_duck_address: String,
    val personal_access_token: String,
)

private class Adapters {
    companion object {
        private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter: JsonAdapter<DuckAddressSetting> = moshi.adapter(DuckAddressSetting::class.java).lenient()
    }
}
