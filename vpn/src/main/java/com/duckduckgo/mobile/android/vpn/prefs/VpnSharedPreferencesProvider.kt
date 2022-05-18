/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.frybits.harmony.getHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
import javax.inject.Inject

interface VpnSharedPreferencesProvider {
    /**
     * Returns an instance of Shared preferences
     * @param name Name of the shared preferences
     * @param multiprocess `true` if the shared preferences will be accessed from several processes else `false`
     * @param migrate `true` if the shared preferences existed prior to use the [VpnSharedPreferencesProvider], else `false`
     */
    fun getSharedPreferences(name: String, multiprocess: Boolean = false, migrate: Boolean = false): SharedPreferences
}

private const val MIGRATED_TO_HARMONY = "migrated_to_harmony"

@ContributesBinding(AppScope::class)
class VpnSharedPreferencesProviderImpl @Inject constructor(
    private val context: Context
) : VpnSharedPreferencesProvider {
    override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences {
        return if (multiprocess) {
            if (migrate) {
                Timber.v("Migrate and return preferences to Harmony")
                return migrateToHarmonyIfNecessary(name)
            } else {
                Timber.v("Return Harmony preferences")
                context.getHarmonySharedPreferences(name)
            }
        } else {
            context.getSharedPreferences(name, MODE_PRIVATE)
        }
    }

    private fun migrateToHarmonyIfNecessary(name: String): SharedPreferences {
        val origin = context.getSharedPreferences(name, MODE_PRIVATE)
        val destination = context.getHarmonySharedPreferences(name)

        if (destination.getBoolean(MIGRATED_TO_HARMONY, false)) return destination
        Timber.v("Performing migration to Harmony")

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
                else -> Timber.w("Could not migrate $key from $name preferences")
            }
        }

        destination.edit(commit = true) { putBoolean(MIGRATED_TO_HARMONY, true) }

        return destination
    }
}
