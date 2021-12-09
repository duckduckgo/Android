/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.FileUtilities.loadText
import com.duckduckgo.app.trackerdetection.model.TdsDomainEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TdsDomainEntityJsonTest {

    private val actionConverter = ActionJsonAdapter()
    private val moshi = Moshi.Builder().add(actionConverter).build()
    private val jsonAdapter: JsonAdapter<TdsJson> = moshi.adapter(TdsJson::class.java)

    @Test
    fun whenFormatIsValidThenDomainEntitiesAreCreated() {
        val json = loadText("json/tds_domain_entities.json")
        val domains = jsonAdapter.fromJson(json)!!.jsonToDomainEntities()
        assertEquals(3, domains.count())
    }

    @Test
    fun whenFormatIsValidThenDomainEntitiesAreConvertedCorrectly() {
        val json = loadText("json/tds_domain_entities.json")
        val domains = jsonAdapter.fromJson(json)!!.jsonToDomainEntities()
        val domain = domains.first()
        assertEquals(TdsDomainEntity("truoptik.com", "21 Productions Inc"), domain)
    }

    @Test
    fun whenValueIsNullThenDomainEntitiesNotCreated() {
        val json = loadText("json/tds_domain_entities_null_value.json")
        val domains = jsonAdapter.fromJson(json)!!.jsonToDomainEntities()
        assertEquals(2, domains.count())
        assertNull(domains.firstOrNull { it.domain == "33across.com" })
    }
}
