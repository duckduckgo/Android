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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguagePackDirectoriesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directories = RealLanguagePackDirectories(context)

    @After
    fun tearDown() {
        context.filesDir.resolve("page-translation").deleteRecursively()
    }

    @Test
    fun listNoPairsWhenNothingWasDownloaded() {
        assertEquals(emptySet<LanguagePair>(), directories.getInstalledPairs())
        assertEquals(emptySet<LanguagePair>(), directories.getPairsWithOldDirectory())
    }

    @Test
    fun listOnlyPairsWithoutSuffixAsInstalled() {
        directories.getPackDirectory(LanguagePair("fr", "en")).mkdirs()
        directories.getStagingDirectory(LanguagePair("de", "en")).mkdirs()
        directories.getOldDirectory(LanguagePair("es", "en")).mkdirs()

        assertEquals(setOf(LanguagePair("fr", "en")), directories.getInstalledPairs())
    }

    @Test
    fun listPairsWithOldDirectory() {
        directories.getPackDirectory(LanguagePair("fr", "en")).mkdirs()
        directories.getStagingDirectory(LanguagePair("de", "en")).mkdirs()
        directories.getOldDirectory(LanguagePair("es", "en")).mkdirs()
        directories.getOldDirectory(LanguagePair("fr", "en")).mkdirs()

        assertEquals(setOf(LanguagePair("es", "en"), LanguagePair("fr", "en")), directories.getPairsWithOldDirectory())
    }

    @Test
    fun skipDirectoriesThatDoNotNameAPair() {
        directories.getPackDirectory(LanguagePair("fr", "en")).mkdirs()
        context.filesDir.resolve("page-translation/packs/unknown").mkdirs()
        context.filesDir.resolve("page-translation/packs/unknown.old").mkdirs()

        assertEquals(setOf(LanguagePair("fr", "en")), directories.getInstalledPairs())
        assertEquals(emptySet<LanguagePair>(), directories.getPairsWithOldDirectory())
    }
}
