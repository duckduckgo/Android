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

package com.duckduckgo.subscriptions.store

import android.content.SharedPreferences
import androidx.core.content.edit

interface AuthDataStore {
    var accessToken: String?
    var authToken: String?
    var email: String?
    var externalId: String?
    var expiresOrRenewsAt: Long?
    var platform: String?
    fun canUseEncryption(): Boolean
}

class AuthEncryptedDataStore(
    private val sharedPrefsProv: SharedPrefsProvider,
) : AuthDataStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        return sharedPrefsProv.getSharedPrefs(FILENAME)
    }

    override var accessToken: String?
        get() = encryptedPreferences?.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_ACCESS_TOKEN)
                } else {
                    putString(KEY_ACCESS_TOKEN, value)
                }
            }
        }

    override var authToken: String?
        get() = encryptedPreferences?.getString(KEY_AUTH_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_AUTH_TOKEN)
                } else {
                    putString(KEY_AUTH_TOKEN, value)
                }
            }
        }

    override var email: String?
        get() = encryptedPreferences?.getString(KEY_EMAIL, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EMAIL)
                } else {
                    putString(KEY_EMAIL, value)
                }
            }
        }

    override var platform: String?
        get() = encryptedPreferences?.getString(KEY_PLATFORM, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_PLATFORM)
                } else {
                    putString(KEY_PLATFORM, value)
                }
            }
        }

    override var externalId: String?
        get() = encryptedPreferences?.getString(KEY_EXTERNAL_ID, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EXTERNAL_ID)
                } else {
                    putString(KEY_EXTERNAL_ID, value)
                }
            }
        }

    override var expiresOrRenewsAt: Long?
        get() = encryptedPreferences?.getLong(KEY_EXPIRES_OR_RENEWS_AT, 0L)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_EXPIRES_OR_RENEWS_AT)
                } else {
                    putLong(KEY_EXPIRES_OR_RENEWS_AT, value)
                }
            }
        }

    override fun canUseEncryption(): Boolean = encryptedPreferences != null

    companion object {
        const val FILENAME = "com.duckduckgo.subscriptions.store"
        const val KEY_ACCESS_TOKEN = "KEY_ACCESS_TOKEN"
        const val KEY_AUTH_TOKEN = "KEY_AUTH_TOKEN"
        const val KEY_PLATFORM = "KEY_PLATFORM"
        const val KEY_EMAIL = "KEY_EMAIL"
        const val KEY_EXTERNAL_ID = "KEY_EXTERNAL_ID"
        const val KEY_EXPIRES_OR_RENEWS_AT = "KEY_EXPIRES_OR_RENEWS_AT"
    }
}
