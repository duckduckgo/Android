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

package com.duckduckgo.translations.impl

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class RealModelStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = mock()

    private lateinit var testee: RealModelStore

    @Before
    fun setup() {
        whenever(context.filesDir).thenReturn(temporaryFolder.root)
        testee = RealModelStore(context)
    }

    @Test
    fun whenPairHasUppercaseThenModelDirLowercasesIt() {
        val dir = testee.modelDir("EN", "Es")

        assertEquals(File(temporaryFolder.root, "translations/enes"), dir)
    }

    @Test
    fun whenDirectoryMissingThenIsDownloadedReturnsFalse() {
        assertFalse(testee.isDownloaded("en", "es"))
    }

    @Test
    fun whenDirectoryHasNoModelBinaryThenIsDownloadedReturnsFalse() {
        modelDir("en", "es").apply {
            mkdirs()
            File(this, "lex.50.50.enes.s2t.bin").createNewFile()
            File(this, "srcvocab.enes.spm").createNewFile()
        }

        assertFalse(testee.isDownloaded("en", "es"))
    }

    @Test
    fun whenModelBinaryPresentThenIsDownloadedReturnsTrue() {
        writeCompletePair("en", "es")

        assertTrue(testee.isDownloaded("en", "es"))
    }

    @Test
    fun whenDirectoryMissingThenResolveReturnsNull() {
        assertNull(testee.resolve("en", "es"))
    }

    @Test
    fun whenShortlistMissingThenResolveReturnsNull() {
        modelDir("en", "es").apply {
            mkdirs()
            File(this, "model.enes.intgemm.alphas.bin").createNewFile()
            File(this, "srcvocab.enes.spm").createNewFile()
        }

        assertNull(testee.resolve("en", "es"))
    }

    @Test
    fun whenVocabMissingThenResolveReturnsNull() {
        modelDir("en", "es").apply {
            mkdirs()
            File(this, "model.enes.intgemm.alphas.bin").createNewFile()
            File(this, "lex.50.50.enes.s2t.bin").createNewFile()
        }

        assertNull(testee.resolve("en", "es"))
    }

    @Test
    fun whenAllFilesPresentThenResolveReturnsThemWithSourceVocabBeforeTargetVocab() {
        val dir = modelDir("en", "es").apply { mkdirs() }
        val model = File(dir, "model.enes.intgemm.alphas.bin").also { it.createNewFile() }
        val shortlist = File(dir, "lex.50.50.enes.s2t.bin").also { it.createNewFile() }
        val targetVocab = File(dir, "trgvocab.enes.spm").also { it.createNewFile() }
        val sourceVocab = File(dir, "srcvocab.enes.spm").also { it.createNewFile() }

        val resolved = testee.resolve("en", "es")!!

        assertEquals(model.absolutePath, resolved.modelPath)
        assertEquals(shortlist.absolutePath, resolved.shortlistPath)
        // srcvocab must sort before trgvocab — the source-then-target order the engine expects.
        assertEquals(listOf(sourceVocab.absolutePath, targetVocab.absolutePath), resolved.vocabPaths)
    }

    private fun modelDir(source: String, target: String): File =
        File(File(temporaryFolder.root, "translations"), "$source$target")

    private fun writeCompletePair(source: String, target: String) {
        modelDir(source, target).apply {
            mkdirs()
            File(this, "model.$source$target.intgemm.alphas.bin").createNewFile()
            File(this, "lex.50.50.$source$target.s2t.bin").createNewFile()
            File(this, "vocab.$source$target.spm").createNewFile()
        }
    }
}
