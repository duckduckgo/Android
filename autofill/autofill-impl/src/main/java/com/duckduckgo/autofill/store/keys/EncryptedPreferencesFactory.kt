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

package com.duckduckgo.autofill.store.keys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Factory interface for creating EncryptedSharedPreferences.
 * Extracted for testability - allows injecting fake implementations in tests.
 */
interface EncryptedPreferencesFactory {
    /**
     * Creates an EncryptedSharedPreferences instance with the given filename.
     * @param filename The name of the preferences file
     * @return SharedPreferences instance
     * @throws Exception if creation fails (e.g., keystore issues)
     */
    fun create(filename: String): SharedPreferences
}

@ContributesBinding(AppScope::class)
class RealEncryptedPreferencesFactory @Inject constructor(
    private val context: Context,
) : EncryptedPreferencesFactory {

    override fun create(filename: String): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            filename,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
