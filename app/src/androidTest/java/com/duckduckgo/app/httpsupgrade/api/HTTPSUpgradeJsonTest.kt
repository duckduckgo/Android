/*
 * Copyright (c) 2017 DuckDuckGo
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

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test


class HTTPSUpgradeJsonTest {

    @Test
    fun whenGivenValidJsonThenParsesCorrectly() {
        val moshi = Moshi.Builder().add(HTTPSUpgradeDomainFromStringAdapter()).build()
        val adapter = moshi.adapter(HTTPSUpgradeJson::class.java)
        val list = adapter.fromJson(json())
        assertEquals(5, list.simpleUpgrade.top500.count())
    }

    private fun json() : String = """
        { "simpleUpgrade" : { "top500": [
            "1337x.to",
            "1688.com",
            "2ch.net",
            "adobe.com",
            "alibaba.com"
        ]}}
    """

}