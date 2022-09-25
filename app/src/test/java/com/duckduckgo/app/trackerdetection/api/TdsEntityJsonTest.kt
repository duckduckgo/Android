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
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class TdsEntityJsonTest {

    private val actionConverter = ActionJsonAdapter()
    private val moshi = Moshi.Builder().add(actionConverter).build()
    private val jsonAdapter: JsonAdapter<TdsJson> = moshi.adapter(TdsJson::class.java)

    @Test
    fun whenFormatIsValidThenEntitiesAreCreated() {
        val json = loadText(javaClass.classLoader!!, "json/tds_entities.json")
        val entities = jsonAdapter.fromJson(json)!!.jsonToEntities()
        assertEquals(4, entities.count())
    }

    @Test
    fun whenFormatIsValidThenBasicElementsAreConvertedCorrectly() {
        val json = loadText(javaClass.classLoader!!, "json/tds_entities.json")
        val entities = jsonAdapter.fromJson(json)!!.jsonToEntities()
        val entity = entities.first()
        assertEquals(TdsEntity("21 Productions Inc", "21 Productions", 0.348), entity)
    }

    @Test
    fun whenEntityIsMissingPrevalenceThenPrevalenceIsSetToZero() {
        val json = loadText(javaClass.classLoader!!, "json/tds_entities.json")
        val entities = jsonAdapter.fromJson(json)!!.jsonToEntities()
        val entity = entities[1]
        assertEquals(0.0, entity.prevalence, 0.0001)
    }

    @Test
    fun whenEntityIsMissingDisplayNameThenDisplayNameIsSameAsName() {
        val json = loadText(javaClass.classLoader!!, "json/tds_entities.json")
        val entities = jsonAdapter.fromJson(json)!!.jsonToEntities()
        val entity = entities[2]
        assertEquals("4Cite Marketing", entity.displayName)
    }

    @Test
    fun whenEntityHasBlankDisplayNameThenDisplayNameIsSameAsName() {
        val json = loadText(javaClass.classLoader!!, "json/tds_entities.json")
        val entities = jsonAdapter.fromJson(json)!!.jsonToEntities()
        val entity = entities.last()
        assertEquals("AT Internet", entity.displayName)
    }
}
