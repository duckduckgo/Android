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

package com.duckduckgo.app.trackerdetection.db

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Copies the blocked-tracker log that app.db kept before it moved to tracker_detection.db, then drops the app.db table.
 *
 * The copy and the import marker commit in one tracker_detection.db transaction, and clearing the log writes the marker too,
 * so a crash or a retry never duplicates rows and never brings back rows the user cleared.
 * Remove this class and the DAO's legacy-import methods once most installs have run it, and drop any leftover table in an
 * app.db migration.
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
        trackerDetectionDatabase.runInTransaction {
            if (!dao.isLegacyImportDone()) {
                copyLegacyRows(dao)
                dao.markLegacyImportDone()
            }
        }
        appDatabase.openHelper.writableDatabase.execSQL("DROP TABLE IF EXISTS `$LEGACY_TABLE`")
    }

    private fun hasLegacyTable(): Boolean =
        appDatabase.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(LEGACY_TABLE)).use { it.moveToFirst() }

    // Ids are regenerated because rows written since the upgrade already use ids in the new table.
    private fun copyLegacyRows(dao: WebTrackersBlockedDao) {
        appDatabase.query("SELECT trackerUrl, trackerCompany, timestamp FROM `$LEGACY_TABLE`", null).use { cursor ->
            val trackerUrl = cursor.getColumnIndexOrThrow("trackerUrl")
            val trackerCompany = cursor.getColumnIndexOrThrow("trackerCompany")
            val timestamp = cursor.getColumnIndexOrThrow("timestamp")
            val batch = ArrayList<WebTrackerBlocked>(BATCH_SIZE)
            while (cursor.moveToNext()) {
                batch += WebTrackerBlocked(
                    trackerUrl = cursor.getString(trackerUrl),
                    trackerCompany = cursor.getString(trackerCompany),
                    timestamp = cursor.getString(timestamp),
                )
                if (batch.size == BATCH_SIZE) {
                    dao.insertAll(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) dao.insertAll(batch)
        }
    }

    private companion object {
        const val LEGACY_TABLE = "web_trackers_blocked"

        // The weekly cleaner lets the table reach about two weeks of rows, so the copy streams instead of loading them all.
        const val BATCH_SIZE = 500
    }
}
