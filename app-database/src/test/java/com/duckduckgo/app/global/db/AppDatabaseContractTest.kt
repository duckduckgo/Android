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

package com.duckduckgo.app.global.db

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `@Database(entities = ...)` uses CLASS retention, so it isn't visible via reflection at runtime.
 * The newest exported schema is the actual contract, so this asserts against it instead.
 */
class AppDatabaseContractTest {

    @Test
    fun whenEntitiesChangeThenThisTestMustBeUpdatedDeliberately() {
        val schemaFile = File("schemas/com.duckduckgo.app.global.db.AppDatabase")
            .listFiles { file -> file.extension == "json" }!!
            .maxBy { it.nameWithoutExtension.toInt() }
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val schema = moshi.adapter<Map<String, Any>>(type).fromJson(schemaFile.readText())!!
        val database = schema["database"] as Map<*, *>
        val tableNames = (database["entities"] as List<*>).map { (it as Map<*, *>)["tableName"] as String }.toSet()

        assertEquals(
            "app.db is legacy and must only ever shrink. Adding a table here is not allowed — " +
                "new storage belongs in the owning feature's -impl module. If you removed a table, " +
                "remove it from this set.",
            EXPECTED_TABLES,
            tableNames,
        )
    }

    private companion object {
        val EXPECTED_TABLES = setOf(
            "app_days_used",
            "app_enjoyment",
            "auth_cookies_allowed_domains",
            "bookmark_folders",
            "bookmarks",
            "default_browser_prompts_app_usage",
            "dismissed_cta",
            "duck_ai_tab_session",
            "entities",
            "favorites",
            "fireproofWebsites",
            "locationPermissions",
            "network_leaderboard",
            "notification",
            "page_loaded_pixel_entity",
            "page_painted_pixel_entity",
            "pixel_store",
            "privacy_protection_count",
            "relations",
            "search_count",
            "sites_visited",
            "survey",
            "tab_page_context",
            "tab_selection",
            "tabs",
            "userStage",
            "user_events",
            "user_whitelist",
            "web_trackers_blocked",
            "webview_sessions",
        )
    }
}
