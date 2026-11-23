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

package com.duckduckgo.pagetranslation.impl.manifest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
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

private const val MANIFEST_PREFERENCES_NAME = "page_translation_manifest"

private val Context.manifestPreferences by preferencesDataStore(name = MANIFEST_PREFERENCES_NAME)

interface ModelsManifestStore {
    /** Returns null when no manifest is stored or the stored one can't be read. */
    suspend fun readManifest(): ModelsManifest?

    /** Returns null, and forgets the ETag, when the stored manifest can't be read, so the next request fetches it in full. */
    suspend fun getEtagForRequest(): String?

    /** Replaces the stored manifest and its ETag, unless the body isn't a manifest this app supports. */
    suspend fun replaceIfSupported(body: String, etag: String?)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealModelsManifestStore internal constructor(
    private val directory: () -> File,
    private val preferences: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : ModelsManifestStore {
    @Inject
    constructor(
        context: Context,
        dispatchers: DispatcherProvider,
    ) : this(
        { context.filesDir.resolve(MANIFEST_DIRECTORY) },
        context.manifestPreferences,
        dispatchers,
    )

    override suspend fun readManifest(): ModelsManifest? = withContext(dispatchers.io()) {
        try {
            val isAlternateFile = preferences.data.firstOrNull()?.get(ALTERNATE_FILE) ?: return@withContext null

            parse(getManifestFile(isAlternateFile).readText())
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun getEtagForRequest(): String? = withContext(dispatchers.io()) {
        if (readManifest() != null) {
            return@withContext preferences.data.firstOrNull()?.get(ETAG)
        }

        preferences.edit { prefs ->
            prefs[ALTERNATE_FILE]?.let { isAlternateFile ->
                Files.deleteIfExists(getManifestFile(isAlternateFile).toPath())
            }

            prefs.remove(ALTERNATE_FILE)
            prefs.remove(ETAG)
        }

        null
    }

    override suspend fun replaceIfSupported(body: String, etag: String?): Unit = withContext(dispatchers.io()) {
        if (parse(body) == null) {
            logcat(ERROR) { "Rejected page translation models manifest" }
            return@withContext
        }

        // preserve the previous file and ETag if the update fails
        preferences.edit { prefs ->
            val isAlternateFile = prefs[ALTERNATE_FILE] == false
            val target = getManifestFile(isAlternateFile).toPath()
            val temp = target.resolveSibling(TEMP_MANIFEST_FILE_NAME)

            Files.createDirectories(target.parent)
            Files.write(temp, body.toByteArray())
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)

            prefs[ALTERNATE_FILE] = isAlternateFile
            if (etag == null) {
                prefs.remove(ETAG)
            } else {
                prefs[ETAG] = etag
            }
        }
    }

    private fun getManifestFile(isAlternateFile: Boolean): File {
        val fileName = if (isAlternateFile) ALTERNATE_MANIFEST_FILE_NAME else MANIFEST_FILE_NAME

        return directory().resolve(fileName)
    }

    private fun parse(body: String): ModelsManifest? = try {
        adapter.fromJson(body)?.takeIf { it.schemaVersion <= SUPPORTED_SCHEMA_VERSION }
    } catch (_: JsonDataException) {
        null
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1

        const val MANIFEST_DIRECTORY = "page-translation"
        const val MANIFEST_FILE_NAME = "models.json"
        const val ALTERNATE_MANIFEST_FILE_NAME = "models-alt.json"
        const val TEMP_MANIFEST_FILE_NAME = "models.tmp"

        const val ALTERNATE_FILE_KEY = "alternate_file"
        const val ETAG_KEY = "etag"

        val ALTERNATE_FILE = booleanPreferencesKey(ALTERNATE_FILE_KEY)
        val ETAG = stringPreferencesKey(ETAG_KEY)

        val adapter: JsonAdapter<ModelsManifest> = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(ModelsManifest::class.java)
    }
}
