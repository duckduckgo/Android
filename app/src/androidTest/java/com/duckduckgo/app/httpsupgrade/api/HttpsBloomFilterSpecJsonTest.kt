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

package com.duckduckgo.app.httpsupgrade.api

import com.duckduckgo.app.httpsupgrade.model.HttpsBloomFilterSpec
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpsBloomFilterSpecJsonTest {

    @Test
    fun whenGivenValidJsonThenParsesCorrectly() {
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(HttpsBloomFilterSpec::class.java)
        val result = jsonAdapter.fromJson(json())!!
        assertEquals(2858372, result.totalEntries)
        assertEquals(0.0001, result.errorRate, 0.00001)
        assertEquals("932ae1481fc33d94320a3b072638c0df8005482506933897e35feb1294693c84", result.sha256)
    }

    private fun json(): String = """
        {
          "totalEntries":2858372,
          "errorRate" : 0.0001,
          "sha256" : "932ae1481fc33d94320a3b072638c0df8005482506933897e35feb1294693c84"
        }
        """
}
