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

/**
 * v2 introduces a new `buckets` field on each interval object inside the `active_intervals`
 * JSON column. Prioritizing safety and simplicity, we drop any wide event with in-flight
 * intervals instead of backfilling the field on legacy rows. Wide events without active
 * intervals are kept.
 *
 * At the time of writing, no wide event interval is expected to stay active for more than
 * a few seconds, so this migration is unlikely to delete anything in practice. We still
 * want an up-to-date schema so that `WideEventRepository` does not need to handle any
 * migration-related edge cases. We may start using long-running intervals in the future,
 * but they won't be affected by this migration.
 *
 * Wide events are analytics-only, so losing some in-flight ones during an upgrade is
 * acceptable.
 */
internal val WideEventsMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM wide_events WHERE active_intervals != '[]'")
    }
}
