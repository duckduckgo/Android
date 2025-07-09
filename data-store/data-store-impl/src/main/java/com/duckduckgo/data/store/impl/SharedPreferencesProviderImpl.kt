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

package com.duckduckgo.data.store.impl

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.LogPriority.WARN
import logcat.logcat

private const val MIGRATED_TO_HARMONY = "migrated_to_harmony"

@ContributesBinding(AppScope::class)
class SharedPreferencesProviderImpl @Inject constructor(
    private val context: Context,
) : SharedPreferencesProvider {
    override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences {
        return if (multiprocess) {
            if (migrate) {
                logcat { "Migrate and return preferences to Harmony" }
                return migrateToHarmonyIfNecessary(name)
            } else {
                logcat { "Return Harmony preferences" }
                context.getHarmonySharedPreferences(name)
            }
        } else {
            context.getSharedPreferences(name, MODE_PRIVATE)
        }
    }

    override fun getEncryptedSharedPreferences(
        name: String,
        multiprocess: Boolean,
    ): SharedPreferences? {
        return runCatching { getEncryptedSharedPreferencesInternal(name, multiprocess) }.getOrNull()
    }

    private fun getEncryptedSharedPreferencesInternal(
        name: String,
        multiprocess: Boolean,
    ): SharedPreferences {
        val prefs = if (multiprocess) {
            context.getEncryptedHarmonySharedPreferences(
                name,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } else {
            EncryptedSharedPreferences.create(
                context,
                name,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        return SafeSharedPreferences(prefs)
    }

    private fun migrateToHarmonyIfNecessary(name: String): SharedPreferences {
        val destination = context.getHarmonySharedPreferences(name)

        if (destination.getBoolean(MIGRATED_TO_HARMONY, false)) return destination
        val origin = context.getSharedPreferences(name, MODE_PRIVATE)
        logcat { "Performing migration to Harmony" }

        val contents = origin.all

        contents.keys.forEach { key ->
            when (val originalValue = contents[key]) {
                is Boolean -> {
                    destination.edit { putBoolean(key, originalValue) }
                }
                is Long -> {
                    destination.edit { putLong(key, originalValue) }
                }
                is Int -> {
                    destination.edit { putInt(key, originalValue) }
                }
                is Float -> {
                    destination.edit { putFloat(key, originalValue) }
                }
                is String -> {
                    destination.edit { putString(key, originalValue) }
                }
                else -> logcat(WARN) { "Could not migrate $key from $name preferences" }
            }
        }

        destination.edit(commit = true) { putBoolean(MIGRATED_TO_HARMONY, true) }

        return destination
    }
}
