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

package com.duckduckgo.app.privacy.db

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.trackerdetection.db.TrackerDetectionDatabase
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Copies the blocked-tracker log that app.db kept before it moved to tracker_detection.db, then drops the app.db table.
 *
 * Each batch commits in its own tracker_detection.db transaction together with the app.db id it reached. A crash or a retry
 * resumes after that id without duplicating rows, and other users of the database, such as the tracker list loading at startup,
 * wait for one batch rather than the whole copy. Clearing the log marks the import done, so old rows are never copied in after
 * the user cleared it.
 */
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class WebTrackersBlockedLegacyImporter @Inject constructor(
    private val appDatabase: AppDatabase,
    private val trackerDetectionDatabase: TrackerDetectionDatabase,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.io()) {
            runCatching { importAndDropLegacyTable() }
                .onFailure { logcat(ERROR) { "Blocked-tracker log import failed, will retry on next start: ${it.asLog()}" } }
        }
    }

    private fun importAndDropLegacyTable() {
        if (!hasLegacyTable()) return

        val dao = trackerDetectionDatabase.webTrackersBlockedDao()
        do {
            val copiedBatch = trackerDetectionDatabase.runInTransaction(Callable { copyNextBatch(dao) })
        } while (copiedBatch)
        appDatabase.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS `$LEGACY_TABLE`")
    }

    private fun hasLegacyTable(): Boolean =
        appDatabase.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(LEGACY_TABLE)).use { it.moveToFirst() }

    // Returns false once nothing is left to copy.
    private fun copyNextBatch(dao: WebTrackersBlockedDao): Boolean {
        val progress = dao.legacyImport()
        if (progress.done) return false

        val batch = readLegacyBatch(afterId = progress.lastImportedId)
        if (batch.isEmpty()) {
            dao.markLegacyImportDone()
            return false
        }
        dao.insertAll(batch.map { it.tracker })
        dao.saveLegacyImport(progress.copy(lastImportedId = batch.last().legacyId))
        return true
    }

    // Ids are regenerated because rows written since the upgrade already use ids in the new table.
    private fun readLegacyBatch(afterId: Long): List<LegacyRow> =
        appDatabase.query(
            "SELECT id, trackerUrl, trackerCompany, timestamp FROM `$LEGACY_TABLE` WHERE id > ? ORDER BY id LIMIT $BATCH_SIZE",
            arrayOf(afterId),
        ).use { cursor ->
            val id = cursor.getColumnIndexOrThrow("id")
            val trackerUrl = cursor.getColumnIndexOrThrow("trackerUrl")
            val trackerCompany = cursor.getColumnIndexOrThrow("trackerCompany")
            val timestamp = cursor.getColumnIndexOrThrow("timestamp")
            buildList {
                while (cursor.moveToNext()) {
                    val tracker = WebTrackerBlocked(
                        trackerUrl = cursor.getString(trackerUrl),
                        trackerCompany = cursor.getString(trackerCompany),
                        timestamp = cursor.getString(timestamp),
                    )
                    add(LegacyRow(legacyId = cursor.getLong(id), tracker = tracker))
                }
            }
        }

    private class LegacyRow(val legacyId: Long, val tracker: WebTrackerBlocked)

    private companion object {
        const val LEGACY_TABLE = "web_trackers_blocked"

        // tracker_detection.db uses TRUNCATE journaling, so every other reader and writer of it waits while a batch runs.
        const val BATCH_SIZE = 500
    }
}
