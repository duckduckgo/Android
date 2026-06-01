/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.location.migration

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.location.permissions.api.LocationPermissionEntity
import com.duckduckgo.location.permissions.api.LocationPermissionType
import com.duckduckgo.location.permissions.api.LocationPermissionsRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

/**
 * One-time copy of legacy location permissions out of the monolithic [AppDatabase] and into the new
 * standalone [com.duckduckgo.location.permissions.store.LocationPermissionsDatabase].
 *
 * Why this exists (the "own database" trade-off):
 * A Room migration on [AppDatabase] cannot write to a *different* database, so the rows that used to
 * live in `AppDatabase.locationPermissions` cannot be moved by `MIGRATION_61_TO_62`. They must be
 * copied at runtime. The legacy table is intentionally NOT dropped by `MIGRATION_61_TO_62` (see the
 * comment there) so it is still readable here.
 *
 * Ordering matters: [com.duckduckgo.app.global.migrations.LocationPermissionMigrationPlugin] (a
 * MigrationPlugin run by MigrationLifecycleObserver) reads location permissions via
 * [LocationPermissionsRepository] — which now points at the NEW database — and copies "always
 * allow/deny" entries into the site-permissions system for users who have not migrated yet. If this
 * copy has not happened before that plugin reads, those users silently lose their location settings.
 *
 * To guarantee the copy wins the race:
 *  - This observer is annotated with [PriorityKey] (0) so it is registered with the
 *    ProcessLifecycleOwner BEFORE MigrationLifecycleObserver (which has no PriorityKey and therefore
 *    sorts last). See the generated PluginPoint ordering: keyed plugins precede non-keyed ones.
 *  - The copy runs SYNCHRONOUSLY inside onCreate. LocationPermissionMigrationPlugin.run() dispatches
 *    its read onto an IO coroutine, so doing the copy on a background thread here would re-introduce
 *    the race. A blocking one-time copy of a small table at startup is the price of correctness for
 *    this design. (This is one of the costs the "own database" option pays vs. an in-DB move.)
 *
 * Idempotent + guarded by a dedicated persisted flag so it runs exactly once per install, regardless
 * of the unrelated MigrationLifecycleObserver version counter.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@PriorityKey(PRIORITY)
@SingleInstanceIn(AppScope::class)
class LocationPermissionsDbCopyObserver @Inject constructor(
    private val context: Context,
    private val appDatabase: Lazy<AppDatabase>,
    private val locationPermissionsRepository: Lazy<LocationPermissionsRepository>,
) : MainProcessLifecycleObserver {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override fun onCreate(owner: LifecycleOwner) {
        if (preferences.getBoolean(KEY_COPIED, false)) return

        runCatching {
            copyLegacyRows()
        }.onFailure {
            // Do not mark as done if the copy failed - we want to retry on the next launch rather
            // than silently drop the user's data.
            logcat(ERROR) { "Location permissions DB copy failed: ${it.asLog()}" }
            return
        }

        preferences.edit { putBoolean(KEY_COPIED, true) }
    }

    private fun copyLegacyRows() {
        val repository = locationPermissionsRepository.get()
        val legacyRows = readLegacyRows()
        logcat { "Location permissions DB copy: found ${legacyRows.size} legacy rows" }
        legacyRows.forEach { entity ->
            repository.savePermissionEntity(entity)
        }
    }

    /**
     * Reads the legacy `locationPermissions` table directly via SQL. The Room @Entity/@Dao for this
     * table were removed from [AppDatabase] when the feature was extracted, but the physical table is
     * still present (MIGRATION_61_TO_62 does not drop it), so a raw query is the cleanest way to read
     * it without re-introducing a transitional Room entity into the app database.
     */
    private fun readLegacyRows(): List<LocationPermissionEntity> {
        val db = appDatabase.get().openHelper.writableDatabase
        val rows = mutableListOf<LocationPermissionEntity>()
        // Guarded read: if a fresh install never had this table, the query throws and we treat it as empty.
        runCatching {
            db.query("SELECT `domain`, `permission` FROM `locationPermissions`").use { cursor ->
                val domainIndex = cursor.getColumnIndex("domain")
                val permissionIndex = cursor.getColumnIndex("permission")
                if (domainIndex < 0 || permissionIndex < 0) return emptyList()
                while (cursor.moveToNext()) {
                    val domain = cursor.getString(domainIndex)
                    val permissionValue = cursor.getInt(permissionIndex)
                    val permissionType = LocationPermissionType.fromValue(permissionValue) ?: continue
                    rows.add(LocationPermissionEntity(domain = domain, permission = permissionType))
                }
            }
        }.onFailure {
            logcat { "Location permissions DB copy: legacy table not readable (likely a fresh install): ${it.asLog()}" }
            return emptyList()
        }
        return rows
    }

    companion object {
        // Must be < the (unkeyed) MigrationLifecycleObserver so the copy runs first.
        const val PRIORITY = 0
        const val FILENAME = "com.duckduckgo.app.location.migration.dbcopy"
        const val KEY_COPIED = "KEY_LOCATION_PERMISSIONS_COPIED_TO_OWN_DB"
    }
}
