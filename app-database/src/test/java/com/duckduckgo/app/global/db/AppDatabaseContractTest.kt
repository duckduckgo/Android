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
    fun whenEntityCountChangesThenThisTestMustBeUpdatedDeliberately() {
        val schemaFile = File("schemas/com.duckduckgo.app.global.db.AppDatabase")
            .listFiles { file -> file.extension == "json" }!!
            .maxBy { it.nameWithoutExtension.toInt() }
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val schema = moshi.adapter<Map<String, Any>>(type).fromJson(schemaFile.readText())!!
        val database = schema["database"] as Map<*, *>
        val entities = database["entities"] as List<*>

        assertEquals(
            "app.db is legacy and must only ever shrink. Adding a table here is not allowed — " +
                "new storage belongs in the owning feature's -impl module. If you removed a table, " +
                "lower this number.",
            35,
            entities.size,
        )
    }
}
