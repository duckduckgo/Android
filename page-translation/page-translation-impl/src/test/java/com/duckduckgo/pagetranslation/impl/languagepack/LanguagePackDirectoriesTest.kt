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
        assertEquals(emptySet<String>(), directories.getInstalledPairs())
        assertEquals(emptySet<String>(), directories.getPairsWithOldDirectory())
    }

    @Test
    fun listOnlyPairsWithoutSuffixAsInstalled() {
        directories.getPackDirectory("fr-en").mkdirs()
        directories.getStagingDirectory("de-en").mkdirs()
        directories.getOldDirectory("es-en").mkdirs()

        assertEquals(setOf("fr-en"), directories.getInstalledPairs())
    }

    @Test
    fun listPairsWithOldDirectory() {
        directories.getPackDirectory("fr-en").mkdirs()
        directories.getStagingDirectory("de-en").mkdirs()
        directories.getOldDirectory("es-en").mkdirs()
        directories.getOldDirectory("fr-en").mkdirs()

        assertEquals(setOf("es-en", "fr-en"), directories.getPairsWithOldDirectory())
    }
}
