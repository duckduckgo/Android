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

package com.duckduckgo.app.statistics.model

import com.duckduckgo.app.FileUtilities.loadText
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtbJsonTest {

    private val moshi = Moshi.Builder().build()
    private val jsonAdapter: JsonAdapter<Atb> = moshi.adapter(Atb::class.java)

    @Test
    fun whenFormatIsValidThenDataIsConverted() {
        val json = loadText(javaClass.classLoader!!, "json/atb_response_valid.json")
        val atb = jsonAdapter.fromJson(json)!!
        assertEquals("v105-3", atb.version)
    }

    @Test(expected = JsonEncodingException::class)
    fun whenFormatIsInvalidThenExceptionIsThrown() {
        assertNull(jsonAdapter.fromJson("invalid"))
    }
}
