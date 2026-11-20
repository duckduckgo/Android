/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import javax.inject.Inject

private val Context.manifestPreferences by preferencesDataStore(name = "page_translation_manifest")

internal class ModelsManifestStore(
    private val directory: () -> File,
    private val preferences: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) {
    @Inject constructor(context: Context, dispatchers: DispatcherProvider) : this(
        { context.filesDir.resolve("page-translation") },
        context.manifestPreferences,
        dispatchers,
    )

    suspend fun manifest(): ModelsManifest? = withContext(dispatchers.io()) {
        try {
            val alternate = preferences.data.firstOrNull()?.get(ALTERNATE_FILE) ?: return@withContext null
            parse(file(alternate).readText())
        } catch (_: IOException) {
            null
        }
    }

    suspend fun etagForRequest(): String? = withContext(dispatchers.io()) {
        if (manifest() != null) return@withContext preferences.data.firstOrNull()?.get(ETAG)
        preferences.edit { prefs ->
            prefs[ALTERNATE_FILE]?.let { Files.deleteIfExists(file(it).toPath()) }
            prefs.remove(ALTERNATE_FILE)
            prefs.remove(ETAG)
        }
        null
    }

    suspend fun replaceIfSupported(body: String, etag: String?): Unit = withContext(dispatchers.io()) {
        if (parse(body) == null) {
            logcat(ERROR) { "Rejected page translation models manifest" }
            return@withContext
        }

        // Switching files and ETag in one preference edit keeps the previous pair usable if the write is interrupted.
        preferences.edit { prefs ->
            val alternate = prefs[ALTERNATE_FILE] == false
            val target = file(alternate).toPath()
            Files.createDirectories(target.parent)
            val temp = target.resolveSibling("models.tmp")
            Files.write(temp, body.toByteArray())
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)
            prefs[ALTERNATE_FILE] = alternate
            if (etag == null) prefs.remove(ETAG) else prefs[ETAG] = etag
        }
    }

    private fun file(alternate: Boolean) = directory().resolve(if (alternate) "models-alt.json" else "models.json")

    private fun parse(body: String): ModelsManifest? = try {
        adapter.fromJson(body)?.takeIf { it.schemaVersion <= SUPPORTED_SCHEMA_VERSION }
    } catch (_: JsonDataException) {
        null
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        val ALTERNATE_FILE = booleanPreferencesKey("alternate_file")
        val ETAG = stringPreferencesKey("etag")
        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(ModelsManifest::class.java)
    }
}
