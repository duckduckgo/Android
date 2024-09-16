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

package com.duckduckgo.subscriptions.impl.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface SubscriptionsDataStore {

    // Auth
    var accessToken: String?
    var authToken: String?
    var email: String?
    var externalId: String?

    // Subscription
    var expiresOrRenewsAt: Long?
    var startedAt: Long?
    var platform: String?
    var status: String?
    var entitlements: String?
    var productId: String?

    fun canUseEncryption(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SubscriptionsEncryptedDataStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : SubscriptionsDataStore {
    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }
    private fun encryptedPreferences(): SharedPreferences? {
        return sharedPreferencesProvider.getEncryptedSharedPreferences(FILENAME, multiprocess = true)
    }

    override var productId: String?
        get() = encryptedPreferences?.getString(KEY_PRODUCT_ID, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_PRODUCT_ID, value)
            }
        }

    override var status: String?
        get() = encryptedPreferences?.getString(KEY_STATUS, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_STATUS, value)
            }
        }

    override var entitlements: String?
        get() = encryptedPreferences?.getString(KEY_ENTITLEMENTS, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_ENTITLEMENTS, value)
            }
        }

    override var accessToken: String?
        get() = encryptedPreferences?.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_ACCESS_TOKEN, value)
            }
        }

    override var authToken: String?
        get() = encryptedPreferences?.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_AUTH_TOKEN, value)
            }
        }

    override var email: String?
        get() = encryptedPreferences?.getString(KEY_EMAIL, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_EMAIL, value)
            }
        }

    override var platform: String?
        get() = encryptedPreferences?.getString(KEY_PLATFORM, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_PLATFORM, value)
            }
        }

    override var externalId: String?
        get() = encryptedPreferences?.getString(KEY_EXTERNAL_ID, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putString(KEY_EXTERNAL_ID, value)
            }
        }

    override var expiresOrRenewsAt: Long?
        get() = encryptedPreferences?.getLong(KEY_EXPIRES_OR_RENEWS_AT, 0L).takeIf { it != 0L }
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EXPIRES_OR_RENEWS_AT)
                } else {
                    putLong(KEY_EXPIRES_OR_RENEWS_AT, value)
                }
            }
        }

    override var startedAt: Long?
        get() = encryptedPreferences?.getLong(KEY_STARTED_AT, 0L).takeIf { it != 0L }
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_STARTED_AT)
                } else {
                    putLong(KEY_STARTED_AT, value)
                }
            }
        }

    override fun canUseEncryption(): Boolean {
        encryptedPreferences?.edit(commit = true) { putBoolean("test", true) }
        return encryptedPreferences?.getBoolean("test", false) == true
    }

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.store"
        const val KEY_ACCESS_TOKEN = "KEY_ACCESS_TOKEN"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
        const val KEY_PLATFORM = "KEY_PLATFORM"
        const val KEY_EMAIL = "KEY_EMAIL"
        const val KEY_EXTERNAL_ID = "KEY_EXTERNAL_ID"
        const val KEY_EXPIRES_OR_RENEWS_AT = "KEY_EXPIRES_OR_RENEWS_AT"
        const val KEY_STARTED_AT = "KEY_STARTED_AT"
        const val KEY_ENTITLEMENTS = "KEY_ENTITLEMENTS"
        const val KEY_STATUS = "KEY_STATUS"
        const val KEY_PRODUCT_ID = "KEY_PRODUCT_ID"
    }
}
