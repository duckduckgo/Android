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
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE_OLD
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.UNDO_DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileWorker.Companion.OPERATION_KEY
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LanguagePackFileWorkerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directories = RealLanguagePackDirectories(context)
    private val packDirectory = directories.getPackDirectory(PAIR)
    private val oldDirectory = directories.getOldDirectory(PAIR)
    private val stagingDirectory = directories.getStagingDirectory(PAIR)

    @After
    fun tearDown() {
        context.filesDir.resolve("page-translation").deleteRecursively()
    }

    @Test
    fun moveThePackAsideAndRemoveLeftoversOnDelete() = runTest {
        packDirectory.createWithFile("current")
        oldDirectory.createWithFile("leftover")
        stagingDirectory.createWithFile("partial")

        assertEquals(Result.success(), run(DELETE))

        assertFalse(packDirectory.exists())
        assertFalse(stagingDirectory.exists())
        assertEquals("current", oldDirectory.readFile())
    }

    @Test
    fun keepThePackMovedAsideWhenDeleteRunsAgain() = runTest {
        packDirectory.createWithFile("current")
        run(DELETE)

        assertEquals(Result.success(), run(DELETE))

        assertEquals("current", oldDirectory.readFile())
    }

    @Test
    fun succeedWithoutChangesWhenDeletingAPairThatIsNotInstalled() = runTest {
        assertEquals(Result.success(), run(DELETE))

        assertEquals(emptySet<String>(), directories.getPairsWithOldDirectory())
    }

    @Test
    fun restoreTheDeletedPackOnUndo() = runTest {
        packDirectory.createWithFile("current")
        run(DELETE)

        assertEquals(Result.success(), run(UNDO_DELETE))

        assertFalse(oldDirectory.exists())
        assertEquals("current", packDirectory.readFile())
    }

    @Test
    fun keepTheReinstalledPackOnUndo() = runTest {
        packDirectory.createWithFile("reinstalled")
        oldDirectory.createWithFile("deleted")

        assertEquals(Result.success(), run(UNDO_DELETE))

        assertEquals("reinstalled", packDirectory.readFile())
    }

    @Test
    fun removeTheDeletedPack() = runTest {
        packDirectory.createWithFile("current")
        run(DELETE)

        assertEquals(Result.success(), run(REMOVE_OLD))

        assertFalse(oldDirectory.exists())
        assertFalse(packDirectory.exists())
    }

    @Test
    fun leaveNothingOfAPairOnRemove() = runTest {
        packDirectory.createWithFile("current")
        oldDirectory.createWithFile("leftover")
        stagingDirectory.createWithFile("partial")
        directories.getPackDirectory(OTHER_PAIR).createWithFile("other")

        assertEquals(Result.success(), run(REMOVE))

        assertEquals(setOf(OTHER_PAIR), context.filesDir.resolve("page-translation/packs").list()!!.toSet())
        assertEquals("other", directories.getPackDirectory(OTHER_PAIR).readFile())
    }

    @Test
    fun removeADeletedPackOnRemove() = runTest {
        oldDirectory.createWithFile("deleted")

        assertEquals(Result.success(), run(REMOVE))

        assertFalse(oldDirectory.exists())
    }

    private suspend fun run(operation: LanguagePackFileOperation) = TestListenableWorkerBuilder<LanguagePackFileWorker>(context)
        .setInputData(workDataOf(PAIR_KEY to PAIR, OPERATION_KEY to operation.name))
        .build()
        .also {
            it.directories = directories
            it.dispatchers = coroutineRule.testDispatcherProvider
        }
        .doWork()

    private fun File.createWithFile(content: String) {
        mkdirs()
        resolve(FILE_NAME).writeText(content)
    }

    private fun File.readFile() = resolve(FILE_NAME).readText()

    private companion object {
        const val PAIR = "fr-en"
        const val OTHER_PAIR = "de-en"
        const val FILE_NAME = "model"
    }
}
