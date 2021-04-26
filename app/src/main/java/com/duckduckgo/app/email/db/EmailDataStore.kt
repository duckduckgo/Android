/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.email.db

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface EmailDataStore {
    var emailToken: String?
    var nextAlias: String?
    var emailUsername: String?
    fun nextAliasFlow(): StateFlow<String?>
}

@FlowPreview
@ExperimentalCoroutinesApi
class EmailEncryptedSharedPreferences(private val context: Context) : EmailDataStore {

    private val nextAliasSharedFlow: MutableStateFlow<String?> = MutableStateFlow(nextAlias)
    override fun nextAliasFlow(): StateFlow<String?> = nextAliasSharedFlow.asStateFlow()

    private val encryptedPreferences: SharedPreferences
        get() = EncryptedSharedPreferences.create(
            context,
            FILENAME,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    override var emailToken: String?
        get() = encryptedPreferences.getString(KEY_EMAIL_TOKEN, null)
        set(value) {
            encryptedPreferences.edit(commit = true) {
                if (value == null) remove(KEY_EMAIL_TOKEN)
                else putString(KEY_EMAIL_TOKEN, value)
            }
        }

    override var nextAlias: String?
        get() = encryptedPreferences.getString(KEY_NEXT_ALIAS, null)
        set(value) {
            encryptedPreferences.edit(commit = true) {
                if (value == null) remove(KEY_NEXT_ALIAS)
                else putString(KEY_NEXT_ALIAS, value)
                GlobalScope.launch {
                    nextAliasSharedFlow.emit(value)
                }
            }
        }

    override var emailUsername: String?
        get() = encryptedPreferences.getString(KEY_EMAIL_USERNAME, null)
        set(value) {
            encryptedPreferences.edit(commit = true) {
                if (value == null) remove(KEY_EMAIL_USERNAME)
                else putString(KEY_EMAIL_USERNAME, value)
            }
        }

    companion object {
        const val FILENAME = "com.duckduckgo.app.email.settings"
        const val KEY_EMAIL_TOKEN = "KEY_EMAIL_TOKEN"
        const val KEY_EMAIL_USERNAME = "KEY_EMAIL_USERNAME"
        const val KEY_NEXT_ALIAS = "KEY_NEXT_ALIAS"
    }
}
