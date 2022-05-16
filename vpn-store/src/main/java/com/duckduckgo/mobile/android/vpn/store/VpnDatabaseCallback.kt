/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.model.VpnState
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import java.util.*
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
internal class VpnDatabaseCallback(
    private val context: Context,
    private val vpnDatabase: Provider<VpnDatabase>,
    private val dispatcherProvider: DispatcherProvider,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        ioThread {
            prepopulateUUID()
            prepopulateAppTrackerBlockingList()
            prepopulateAppTrackerExclusionList()
            prepopulateAppTrackerExceptionRules()
        }
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        ioThread {
            prepopulateUUID()
            prepopulateAppTrackerBlockingList()
            prepopulateAppTrackerExclusionList()
            prepopulateAppTrackerExceptionRules()
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        ioThread {
            prepopulateTrackerEntities()
        }
    }

    private fun prepopulateUUID() {
        val uuid = UUID.randomUUID().toString()
        vpnDatabase.get().vpnStateDao().insert(VpnState(uuid = uuid))
        Timber.w("VPNDatabase: UUID pre-populated as $uuid")
    }

    private fun prepopulateTrackerEntities() {
        context.resources.openRawResource(R.raw.full_app_trackers_blocklist).bufferedReader()
            .use { it.readText() }
            .also {
                val blocklist = getFullAppTrackerBlockingList(it)
                with(vpnDatabase.get().vpnAppTrackerBlockingDao()) {
                    if (!hasTrackerEntities()) {
                        insertTrackerEntities(blocklist.entities)
                    }
                }
            }
    }

    @VisibleForTesting
    internal fun prepopulateAppTrackerBlockingList() {
        context.resources.openRawResource(R.raw.full_app_trackers_blocklist).bufferedReader()
            .use { it.readText() }
            .also {
                val blocklist = getFullAppTrackerBlockingList(it)
                with(vpnDatabase.get().vpnAppTrackerBlockingDao()) {
                    insertTrackerBlocklist(blocklist.trackers)
                    insertAppPackages(blocklist.packages)
                    insertTrackerEntities(blocklist.entities)
                }
            }
    }

    private fun prepopulateAppTrackerExclusionList() {
        context.resources.openRawResource(R.raw.app_tracker_app_exclusion_list).bufferedReader()
            .use { it.readText() }
            .also {
                val excludedAppPackages = parseAppTrackerExclusionList(it)
                vpnDatabase.get().vpnAppTrackerBlockingDao().insertExclusionList(excludedAppPackages)
            }
    }

    private fun prepopulateAppTrackerExceptionRules() {
        context.resources.openRawResource(R.raw.app_tracker_exception_rules).bufferedReader()
            .use { it.readText() }
            .also { json ->
                val rules = parseJsonAppTrackerExceptionRules(json)
                vpnDatabase.get().vpnAppTrackerBlockingDao().insertTrackerExceptionRules(rules)
            }
    }

    private fun getFullAppTrackerBlockingList(json: String): AppTrackerBlocklist {
        return AppTrackerJsonParser.parseAppTrackerJson(Moshi.Builder().build(), json)
    }

    private fun parseAppTrackerExclusionList(json: String): List<AppTrackerExcludedPackage> {
        val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<JsonAppTrackerExclusionList> = moshi.adapter(JsonAppTrackerExclusionList::class.java)
        return adapter.fromJson(json)?.rules.orEmpty().map {
            AppTrackerExcludedPackage(it)
        }
    }

    private fun parseJsonAppTrackerExceptionRules(json: String): List<AppTrackerExceptionRule> {
        val moshi = Moshi.Builder().build()
        val adapter: JsonAdapter<JsonAppTrackerExceptionRules> = moshi.adapter(JsonAppTrackerExceptionRules::class.java)
        return adapter.fromJson(json)?.rules.orEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun ioThread(f: () -> Unit) {
        // At most 1 thread will be doing IO
        dispatcherProvider.io().limitedParallelism(1).asExecutor().execute(f)
    }
}
