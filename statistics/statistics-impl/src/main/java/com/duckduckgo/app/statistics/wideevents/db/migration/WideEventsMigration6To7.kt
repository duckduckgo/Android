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

package com.duckduckgo.app.statistics.wideevents.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONException
import org.json.JSONObject

/**
 * v7 records which process owns each `OnProcessStart` policy. Rows that predate it are attributed to the main process, whose start is
 * what used to clean them up, so they remain eligible for the first cleanup after the upgrade.
 *
 * A policy that cannot be parsed is reset to the client's default so that every row has a readable policy once the migration completes.
 * Aborting instead would leave the database stuck at v6 and fail every wide-event operation until app data is cleared.
 */
internal object WideEventsMigration6To7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val updates = db.query("SELECT id, cleanup_policy FROM wide_events").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val migratedPolicy = migratedPolicy(currentCleanupPolicy = cursor.getString(1))
                    if (migratedPolicy != null) {
                        add(cursor.getLong(0) to migratedPolicy)
                    }
                }
            }
        }

        updates.forEach { (id, policy) ->
            db.execSQL("UPDATE wide_events SET cleanup_policy = ? WHERE id = ?", arrayOf<Any?>(policy, id))
        }
    }

    /** Returns the policy to store, or null when the stored one needs no change. */
    private fun migratedPolicy(currentCleanupPolicy: String?): String? {
        val policy = try {
            JSONObject(currentCleanupPolicy ?: return DEFAULT_POLICY)
        } catch (e: JSONException) {
            return DEFAULT_POLICY
        }

        return when (policy.optString(TYPE_KEY)) {
            PROCESS_START_TYPE -> policy.put(OWNER_PROCESS_NAME_KEY, MAIN_PROCESS_NAME).toString()
            TIMEOUT_TYPE -> null
            else -> DEFAULT_POLICY
        }
    }

    private const val TYPE_KEY = "type"
    private const val PROCESS_START_TYPE = "OnProcessStart"
    private const val TIMEOUT_TYPE = "OnTimeout"
    private const val OWNER_PROCESS_NAME_KEY = "ownerProcessName"
    private const val MAIN_PROCESS_NAME = "main"

    // Serialized form of the WideEventClient.flowStart default, OnTimeout(7.days) with status Unknown.
    private const val DEFAULT_POLICY = """{"type":"OnTimeout","duration":604800000,"status":"unknown","metadata":{}}"""
}
