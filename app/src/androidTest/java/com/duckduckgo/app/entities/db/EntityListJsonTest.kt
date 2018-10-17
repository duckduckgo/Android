/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.entities.db

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.entities.api.NetworkEntityJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertFalse
import org.junit.Test

class EntityListJsonTest {

    @Test
    fun whenJsonIsValidThenParseSucceeds() {
        val json = FileUtilities.loadText("json/entitylist2.json")
        val type = Types.newParameterizedType(Map::class.java, String::class.java, NetworkEntityJson::class.java)
        val adapter = Moshi.Builder().build().adapter<Map<String, NetworkEntityJson>>(type)
        val result = adapter.fromJson(json)
        assertFalse(result.isEmpty())
    }

}