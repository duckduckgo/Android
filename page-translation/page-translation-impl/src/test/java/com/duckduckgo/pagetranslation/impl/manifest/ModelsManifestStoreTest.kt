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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ModelsManifestStoreTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory get() = temporaryFolder.root.resolve(MANIFEST_DIRECTORY_NAME)
    private val preferences = PreferenceDataStoreFactory.create(scope = coroutineRule.testScope) {
        temporaryFolder.root.resolve(MANIFEST_PREFERENCES_FILE_NAME)
    }
    private val store = createStore(preferences)

    @Test
    fun returnNullBeforeFirstWrite() = runTest {
        assertNull(store.readManifest())
        assertNull(store.getEtagForRequest())
    }

    @Test
    fun storeOriginalBodyAndETag() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)

        assertEquals(MANIFEST, getStoredFile().readText())
        val model = store.readManifest()!!.models.getValue(MODEL_PAIR)
        assertEquals(25020240L, model.totalBytes)
        assertEquals(31561787L, model.files.getValue(MODEL_FILE_KEY).decompressedSize)
        assertEquals(OLD_TAG, store.getEtagForRequest())
    }

    @Test
    fun keepBodyAndETagWhenBodyIsMalformed() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)

        val invalidManifests = listOf(
            INCOMPLETE_JSON_OBJECT,
            NULL_JSON,
            MANIFEST + EMPTY_JSON_OBJECT,
            EMPTY_JSON_OBJECT,
            MISSING_MODELS_MANIFEST,
        )

        for (invalid in invalidManifests) {
            store.replaceIfSupported(invalid, NEW_TAG)

            assertEquals(MANIFEST, getStoredFile().readText())
            assertEquals(OLD_TAG, store.getEtagForRequest())
        }
    }

    @Test
    fun keepBodyAndETagWhenSchemaIsUnsupported() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)

        store.replaceIfSupported(FUTURE_SCHEMA_MANIFEST, NEW_TAG)

        assertEquals(MANIFEST, getStoredFile().readText())
        assertEquals(OLD_TAG, store.getEtagForRequest())
    }

    @Test
    fun replaceBodyAndDropMissingETag() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)

        store.replaceIfSupported(UPDATED_MANIFEST, null)

        assertEquals(UPDATED_BASE_URL, store.readManifest()!!.baseUrl)
        assertNull(store.getEtagForRequest())
    }

    @Test
    fun clearETagWhenFileIsMissing() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)
        getStoredFile().delete()

        assertNull(store.getEtagForRequest())
        assertNull(store.readManifest())
    }

    @Test
    fun deleteCorruptFileAndClearETag() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)
        val file = getStoredFile()
        file.writeText(CORRUPT_CONTENT)

        assertNull(store.readManifest())
        assertNull(store.getEtagForRequest())
        assertFalse(file.exists())
    }

    @Test
    fun keepValidModelsAndOnlyTheirContractFiles() = runTest {
        store.replaceIfSupported(
            manifestOf(model("fr-en", "fr", "en", VOCAB_FILES + "extra"), model("ru-en", "ru", "en", SPLIT_VOCAB_FILES)),
            OLD_TAG,
        )

        val models = store.readManifest()!!.models
        assertEquals(setOf("fr-en", "ru-en"), models.keys)
        assertEquals(VOCAB_FILES.toSet(), models.getValue("fr-en").files.keys)
        assertEquals(SPLIT_VOCAB_FILES.toSet(), models.getValue("ru-en").files.keys)
    }

    @Test
    fun dropModelsWithUnusableNamesOrSizes() = runTest {
        store.replaceIfSupported(
            manifestOf(
                model("en-de", "de", "en", VOCAB_FILES),
                model("..-en", "..", "en", VOCAB_FILES),
                model("es-en", "es", "en", listOf("model", "vocab")),
                model("de-en", "de", "en", VOCAB_FILES).replace("\"bytes\":1", "\"bytes\":0"),
                model("pt-en", "pt", "en", VOCAB_FILES).replace("\"decompressedSize\":1", "\"decompressedSize\":-1"),
                model("en-es", "en", "es", VOCAB_FILES).replace("\"totalBytes\":1", "\"totalBytes\":0"),
                model("fr-en", "fr", "en", VOCAB_FILES),
            ),
            OLD_TAG,
        )

        assertEquals(setOf("fr-en"), store.readManifest()!!.models.keys)
    }

    @Test
    fun keepPreviousFileAndETagReadableWhenPreferenceCommitFails() = runTest {
        store.replaceIfSupported(MANIFEST, OLD_TAG)

        val failingPreferences = object : DataStore<Preferences> by preferences {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                return preferences.updateData { current ->
                    transform(current)
                    throw IOException(COMMIT_FAILURE_MESSAGE)
                }
            }
        }

        try {
            createStore(failingPreferences).replaceIfSupported(UPDATED_MANIFEST, NEW_TAG)
            fail(EXPECTED_FAILURE_MESSAGE)
        } catch (_: IOException) {
            val reopened = createStore(preferences)

            assertEquals(ORIGINAL_BASE_URL, reopened.readManifest()!!.baseUrl)
            assertEquals(OLD_TAG, reopened.getEtagForRequest())
        }
    }

    private fun createStore(dataStore: DataStore<Preferences>) =
        RealModelsManifestStore({ directory }, dataStore, coroutineRule.testDispatcherProvider)

    private fun getStoredFile(): File = directory.listFiles()!!.single()

    private fun manifestOf(vararg models: String) = """{"schemaVersion":1,"baseUrl":"/","models":{${models.joinToString(",")}}}"""

    private fun model(pair: String, source: String, target: String, files: List<String>): String {
        val fileEntries = files.joinToString(
            ",",
        ) { """"$it":{"path":"$it.zst","bytes":1,"sha256":"a","decompressedSize":1,"decompressedHash":"b"}""" }

        return """"$pair":{"sourceLanguage":"$source","targetLanguage":"$target","version":"1","architecture":"base-memory",""" +
            """"totalBytes":1,"files":{$fileEntries}}"""
    }

    private companion object {
        const val MANIFEST_PREFERENCES_FILE_NAME = "manifest.preferences_pb"
        const val MANIFEST_DIRECTORY_NAME = "page-translation"

        const val MODEL_PAIR = "fr-en"
        const val MODEL_FILE_KEY = "model"

        const val EMPTY_JSON_OBJECT = "{}"
        const val INCOMPLETE_JSON_OBJECT = "{"
        const val NULL_JSON = "null"
        const val MISSING_MODELS_MANIFEST = """{"schemaVersion":1,"baseUrl":"/"}"""
        const val FUTURE_SCHEMA_MANIFEST = """{"schemaVersion":2,"baseUrl":"/","models":{}}"""
        const val UPDATED_MANIFEST = """{"schemaVersion":1,"baseUrl":"new","models":{}}"""
        const val UPDATED_BASE_URL = "new"
        const val ORIGINAL_BASE_URL = "https://example.test/"
        const val CORRUPT_CONTENT = "broken"
        const val COMMIT_FAILURE_MESSAGE = "Failed to commit preferences"
        const val EXPECTED_FAILURE_MESSAGE = "Expected a failed write"

        const val OLD_TAG = "\"first\""
        val VOCAB_FILES = listOf("model", "lexicalShortlist", "vocab")
        val SPLIT_VOCAB_FILES = listOf("model", "lexicalShortlist", "srcVocab", "trgVocab")
        const val NEW_TAG = "W/\"second\""

        val MANIFEST = """
            {
              "schemaVersion": 1,
              "generated": "2026-11-02T10:14:30Z",
              "baseUrl": "https://example.test/",
              "extra": true,
              "models": {
                "fr-en": {
                  "sourceLanguage": "fr",
                  "targetLanguage": "en",
                  "version": "3.0",
                  "architecture": "base-memory",
                  "totalBytes": 25020240,
                  "files": {
                    "model": {
                      "path": "model.zst",
                      "bytes": 22712124,
                      "sha256": "a",
                      "decompressedSize": 31561787,
                      "decompressedHash": "b"
                    },
                    "lexicalShortlist": {
                      "path": "short.zst",
                      "bytes": 2,
                      "sha256": "c",
                      "decompressedSize": 5,
                      "decompressedHash": "d"
                    },
                    "vocab": {
                      "path": "vocab.zst",
                      "bytes": 3,
                      "sha256": "e",
                      "decompressedSize": 6,
                      "decompressedHash": "f"
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
