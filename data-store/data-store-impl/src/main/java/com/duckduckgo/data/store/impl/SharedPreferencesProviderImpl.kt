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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKeys
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.sanitizeStackTrace
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_ENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_ENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_ENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.data.store.impl.DataStorePixelNames.DATA_STORE_MIGRATE_UNENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED
import com.duckduckgo.di.scopes.AppScope
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

private const val MIGRATED_TO_HARMONY = "migrated_to_harmony"

@ContributesBinding(AppScope::class)
class SharedPreferencesProviderImpl @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    pixelLazy: Lazy<Pixel>,
    dataStoreProviderFeatureLazy: Lazy<DatabaseProviderFeature>,
    private val crashLogger: Lazy<CrashLogger>,
) : SharedPreferencesProvider {

    private val pixel by lazy {
        pixelLazy.get()
    }

    private val dataStoreProviderFeature by lazy {
        dataStoreProviderFeatureLazy.get()
    }

    @SuppressLint("DenyListedApi")
    override fun getSharedPreferences(name: String, multiprocess: Boolean, migrate: Boolean): SharedPreferences {
        val prefs = if (multiprocess) {
            if (migrate) {
                logcat { "Migrate and return preferences to Harmony" }
                migrateToHarmonyIfNecessary(name)
            } else {
                logcat { "Return Harmony preferences" }
                context.getHarmonySharedPreferences(name)
            }
        } else {
            context.getSharedPreferences(name, MODE_PRIVATE)
        }

        return SafeSharedPreferences(prefs, crashLogger.get())
    }

    override fun getEncryptedSharedPreferences(
        name: String,
        multiprocess: Boolean,
    ): SharedPreferences? {
        return runCatching { getEncryptedSharedPreferencesInternal(name, multiprocess) }.getOrNull()
    }

    override suspend fun getMigratedEncryptedSharedPreferences(name: String): SharedPreferences? {
        logcat { "Migrate and return encrypted preferences to Harmony" }
        return migrateEncryptedToHarmonyIfNecessary(name)?.let {
            SafeSharedPreferences(it, crashLogger.get())
        }
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

        return SafeSharedPreferences(prefs, crashLogger.get())
    }

    private fun migrateToHarmonyIfNecessary(name: String): SharedPreferences {
        val destination = runCatching {
            context.getHarmonySharedPreferences(name)
        }.getOrElse {
            pixel.fire(
                DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED,
                mapOf("error" to it.error(), "name" to name),
                type = Pixel.PixelType.Daily(),
            )
            throw it
        }

        runCatching {
            if (destination.getBoolean(MIGRATED_TO_HARMONY, false)) return destination
        }.getOrElse {
            pixel.fire(
                DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED,
                mapOf("error" to it.error(), "name" to name),
                type = Pixel.PixelType.Daily(),
            )
            throw it
        }

        val origin = runCatching {
            context.getSharedPreferences(name, MODE_PRIVATE)
        }.getOrElse {
            pixel.fire(
                DATA_STORE_MIGRATE_UNENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED,
                mapOf("error" to it.error(), "name" to name),
                type = Pixel.PixelType.Daily(),
            )
            throw it
        }

        logcat { "Performing migration to Harmony" }

        val contents: Map<String?, Any?>? = runCatching {
            origin.all
        }.getOrElse {
            pixel.fire(
                DATA_STORE_MIGRATE_UNENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED,
                mapOf("error" to it.error(), "name" to name),
                type = Pixel.PixelType.Daily(),
            )
            throw it
        }

        runCatching {
            contents?.keys?.forEach { key ->
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
                    is Set<*> -> {
                        if (originalValue.all { it is String }) {
                            destination.edit { putStringSet(key, originalValue.filterIsInstance<String>().toSet()) }
                        } else {
                            logcat(WARN) { "Could not migrate $key from $name preferences" }
                        }
                    }
                    else -> logcat(WARN) { "Could not migrate $key from $name preferences" }
                }
            }
            destination.edit(commit = true) { putBoolean(MIGRATED_TO_HARMONY, true) }
        }.getOrElse {
            pixel.fire(
                DATA_STORE_MIGRATE_UNENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED,
                mapOf("error" to it.error(), "name" to name),
                type = Pixel.PixelType.Daily(),
            )
            throw it
        }

        return destination
    }

    private suspend fun migrateEncryptedToHarmonyIfNecessary(name: String): SharedPreferences? {
        return withContext(dispatcherProvider.io()) {
            val destination = runCatching {
                context.getEncryptedHarmonySharedPreferences(
                    name,
                    masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    prefKeyEncryptionScheme = EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    prefValueEncryptionScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_DESTINATION_FAILED,
                    mapOf("error" to it.error(), "name" to name),
                    type = Pixel.PixelType.Daily(),
                )
                return@withContext null
            }

            val alreadyMigrated = runCatching {
                destination.getBoolean(MIGRATED_TO_HARMONY, false)
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    DATA_STORE_MIGRATE_ENCRYPTED_QUERY_PREFERENCES_DESTINATION_FAILED,
                    mapOf("error" to it.error(), "name" to name),
                    type = Pixel.PixelType.Daily(),
                )
                return@withContext null
            }

            if (alreadyMigrated) return@withContext destination

            val origin = runCatching {
                EncryptedSharedPreferences.create(
                    context,
                    name,
                    MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    DATA_STORE_MIGRATE_ENCRYPTED_GET_PREFERENCES_ORIGIN_FAILED,
                    mapOf("error" to it.error(), "name" to name),
                    type = Pixel.PixelType.Daily(),
                )
                return@withContext null
            }

            logcat { "Performing encrypted migration to Harmony" }

            val contents: Map<String?, Any?>? = runCatching {
                origin.all
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    DATA_STORE_MIGRATE_ENCRYPTED_QUERY_ALL_PREFERENCES_ORIGIN_FAILED,
                    mapOf("error" to it.error(), "name" to name),
                    type = Pixel.PixelType.Daily(),
                )
                return@withContext null
            }

            runCatching {
                contents?.keys?.forEach { key ->
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
                        is Set<*> -> {
                            if (originalValue.all { it is String }) {
                                destination.edit { putStringSet(key, originalValue.filterIsInstance<String>().toSet()) }
                            } else {
                                logcat(WARN) { "Could not migrate $key from $name preferences" }
                            }
                        }
                        else -> logcat(WARN) { "Could not migrate $key from $name preferences" }
                    }
                }
                destination.edit(commit = true) { putBoolean(MIGRATED_TO_HARMONY, true) }
            }.getOrElse {
                ensureActive()
                pixel.fire(
                    DATA_STORE_MIGRATE_ENCRYPTED_UPDATE_PREFERENCES_DESTINATION_FAILED,
                    mapOf("error" to it.error(), "name" to name),
                    type = Pixel.PixelType.Daily(),
                )
                return@withContext null
            }

            return@withContext destination
        }
    }

    private fun Throwable.error(): String {
        return if (dataStoreProviderFeature.sendSanitizedStackTraces().isEnabled()) {
            sanitizeStackTrace()
        } else {
            javaClass.name
        }
    }
}
