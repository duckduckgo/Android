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

package com.duckduckgo.pagetranslation.impl.languagepack

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.languagepack.RealLanguagePackInstaller.Companion.MANIFEST_ENTRY_FILE_NAME
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.ModelFile
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestStore
import com.duckduckgo.pagetranslation.impl.translatekit.TranslateKitLibrary
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

class LanguagePackInstallerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val translateKit: TranslateKitLibrary = mock()
    private val store: ModelsManifestStore = mock()
    private val installer = RealLanguagePackInstaller(translateKit, store, coroutineRule.testDispatcherProvider)
    private val stagingDirectory get() = temporaryFolder.root.resolve("$PAIR.tmp")
    private val packDirectory get() = temporaryFolder.root.resolve(PAIR)

    @Before
    fun setUp() = runTest {
        whenever(store.readManifest()).thenReturn(manifestOf(MODEL))
        whenever(translateKit.init()).thenReturn(true)
        doAnswer { it.getArgument<File>(1).writeText(DECOMPRESSED.getValue(it.getArgument<File>(1).nameWithoutExtension)) }
            .whenever(translateKit).decompressZstd(any(), any())

        stagingDirectory.mkdirs()
        COMPRESSED.forEach { (key, content) -> stagingDirectory.resolve("$key.zst").writeText(content) }
    }

    @Test
    fun installDecompressedFilesAndManifestEntry() = runTest {
        assertTrue(installer.install(PAIR, MODEL, stagingDirectory, packDirectory))

        assertFalse(stagingDirectory.exists())
        assertEquals(setOf("model.bin", "vocab.spm", MANIFEST_ENTRY_FILE_NAME), packDirectory.list()!!.toSet())
        assertEquals(DECOMPRESSED["model"], packDirectory.resolve("model.bin").readText())
        assertEquals(DECOMPRESSED["vocab"], packDirectory.resolve("vocab.spm").readText())
        assertEquals(MODEL, adapter.fromJson(packDirectory.resolve(MANIFEST_ENTRY_FILE_NAME).readText()))
    }

    @Test
    fun failWhenCompressedSizeDiffers() = runTest {
        assertNotInstalled(withModelFile { it.copy(bytes = it.bytes + 1) })
    }

    @Test
    fun failWhenCompressedHashDiffers() = runTest {
        assertNotInstalled(withModelFile { it.copy(sha256 = VOCAB_FILE.sha256) })
    }

    @Test
    fun failWhenDecompressedSizeDiffers() = runTest {
        assertNotInstalled(withModelFile { it.copy(decompressedSize = it.decompressedSize + 1) })
    }

    @Test
    fun failWhenDecompressedHashDiffers() = runTest {
        assertNotInstalled(withModelFile { it.copy(decompressedHash = VOCAB_FILE.decompressedHash) })
    }

    @Test
    fun failWhenDecompressionFails() = runTest {
        doThrow(IOException()).whenever(translateKit).decompressZstd(any(), any())

        assertNotInstalled(MODEL)
    }

    @Test
    fun failWhenManifestListsAnotherVersion() = runTest {
        whenever(store.readManifest()).thenReturn(manifestOf(MODEL.copy(version = "4.0")))

        assertNotInstalled(MODEL)
    }

    @Test
    fun failWhenManifestNoLongerListsPair() = runTest {
        whenever(store.readManifest()).thenReturn(manifestOf())

        assertNotInstalled(MODEL)
    }

    @Test
    fun failWhenTranslateKitFailsToInitialize() = runTest {
        whenever(translateKit.init()).thenReturn(false)

        assertNotInstalled(MODEL)
    }

    private suspend fun assertNotInstalled(model: TranslationModel) {
        assertFalse(installer.install(PAIR, model, stagingDirectory, packDirectory))

        assertFalse(stagingDirectory.exists())
        assertFalse(packDirectory.exists())
    }

    private fun manifestOf(vararg models: TranslationModel) =
        ModelsManifest(schemaVersion = 1, baseUrl = "https://models.test/", models = models.associateBy { PAIR })

    private fun withModelFile(change: (ModelFile) -> ModelFile) = MODEL.copy(files = MODEL.files + ("model" to change(MODEL_FILE)))

    private companion object {
        const val PAIR = "fr-en"

        val COMPRESSED = mapOf("model" to "model-compressed", "vocab" to "vocab-compressed")
        val DECOMPRESSED = mapOf("model" to "model-decompressed", "vocab" to "vocab-decompressed")

        val MODEL_FILE = ModelFile(
            path = "fr-en/3.0/base-memory/model.fren.intgemm.alphas.bin.zst",
            bytes = 16,
            sha256 = "b950802ff3b0fd35c94ed5e6476c410e037b214f2be846c6b9d63a5a7197e6a7",
            decompressedSize = 18,
            decompressedHash = "1E25871B30516E0F9D34E009470CA69E6C8BEDC1FBB0174237E21238ACCEF2A9",
        )
        val VOCAB_FILE = ModelFile(
            path = "fr-en/3.0/base-memory/vocab.fren.spm.zst",
            bytes = 16,
            sha256 = "ac5bb24d7f47b8d8aa83bc1480de723af28c5a2ff520a3be7b8dbe54496fd0a3",
            decompressedSize = 18,
            decompressedHash = "e59ae9f33dafefbcce8959f13b82454c4fd0652bf416102273cd5dcfb350c78d",
        )

        val MODEL = TranslationModel(
            sourceLanguage = "fr",
            targetLanguage = "en",
            version = "3.0",
            architecture = "base-memory",
            totalBytes = 32,
            files = mapOf("model" to MODEL_FILE, "vocab" to VOCAB_FILE),
        )

        val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(TranslationModel::class.java)
    }
}
