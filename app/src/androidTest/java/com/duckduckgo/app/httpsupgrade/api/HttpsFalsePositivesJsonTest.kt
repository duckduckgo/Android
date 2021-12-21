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

import com.duckduckgo.app.httpsupgrade.model.HttpsFalsePositiveDomain
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpsFalsePositivesJsonTest {

    @Test
    fun whenGivenValidJsonThenParsesCorrectly() {

        val moshi = Moshi.Builder().add(HttpsFalsePositivesJsonAdapter()).build()
        val type =
            Types.newParameterizedType(List::class.java, HttpsFalsePositiveDomain::class.java)
        val jsonAdapter: JsonAdapter<List<HttpsFalsePositiveDomain>> = moshi.adapter(type)

        val list = jsonAdapter.fromJson(json())!!
        assertEquals(7, list.count())
    }

    private fun json(): String =
        """
        {
            "data": [
                "mlb.mlb.com",
                "proa.accuweather.com",
                "jbhard.org",
                "lody.net",
                "ci.brookfield.wi.us",
                "cocktaildreams.de",
                "anwalt-im-netz.de"
            ]
        }
        """
}
