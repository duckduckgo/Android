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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDownloadWorker.Companion.PAIR_KEY
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.DELETE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.REMOVE_OLD
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFileOperation.UNDO_DELETE
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class LanguagePackFileOperation { DELETE, UNDO_DELETE, REMOVE_OLD, REMOVE }

@ContributesWorker(AppScope::class)
internal class LanguagePackFileWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var directories: LanguagePackDirectories

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        val pair = inputData.getString(PAIR_KEY)
        val operation = LanguagePackFileOperation.entries.firstOrNull { it.name == inputData.getString(OPERATION_KEY) }
        if (pair == null || operation == null) return@withContext Result.failure()

        val packDirectory = directories.getPackDirectory(pair)
        val oldDirectory = directories.getOldDirectory(pair)
        when (operation) {
            DELETE -> moveToOld(pair)
            UNDO_DELETE -> if (oldDirectory.exists() && !packDirectory.exists()) oldDirectory.renameTo(packDirectory)
            REMOVE_OLD -> oldDirectory.deleteRecursively()
            REMOVE -> {
                // the rename uninstalls the pack at once, so a crash while deleting never leaves a partly deleted pack installed
                moveToOld(pair)
                oldDirectory.deleteRecursively()
            }
        }

        Result.success()
    }

    private fun moveToOld(pair: String) {
        directories.getStagingDirectory(pair).deleteRecursively()
        // a rerun after the process died must keep the pack the first run moved aside
        val packDirectory = directories.getPackDirectory(pair)
        if (!packDirectory.exists()) return

        directories.getOldDirectory(pair).deleteRecursively()
        packDirectory.renameTo(directories.getOldDirectory(pair))
    }

    companion object {
        const val OPERATION_KEY = "operation"
    }
}
