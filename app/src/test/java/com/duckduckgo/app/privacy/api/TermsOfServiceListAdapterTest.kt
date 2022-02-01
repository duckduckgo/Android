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

package com.duckduckgo.app.privacy.api

import com.duckduckgo.app.FileUtilities.loadText
import com.duckduckgo.app.privacy.model.TermsOfService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.*
import org.junit.Test

class TermsOfServiceListAdapterTest {

    private val moshi = Moshi.Builder().add(TermsOfServiceListAdapter()).build()
    private val type = Types.newParameterizedType(List::class.java, TermsOfService::class.java)
    private val jsonAdapter: JsonAdapter<List<TermsOfService>> = moshi.adapter(type)

    @Test
    fun whenFormatIsValidThenDataIsCreated() {
        val json = loadText(TermsOfServiceListAdapterTest::class.java.classLoader!!, "json/tosdr.json")
        val terms = jsonAdapter.fromJson(json)
        assertNotNull(terms)
    }

    @Test
    fun whenFormatIsValidThenDataIsConvertedCorrectly() {
        val json = loadText(TermsOfServiceListAdapterTest::class.java.classLoader!!, "json/tosdr.json")
        val terms = jsonAdapter.fromJson(json)!!

        val firstTerm = terms.first { it.name == "example.com" }
        assertEquals(-20, firstTerm.score)
        assertNull(firstTerm.classification)
        assertEquals(1, firstTerm.goodPrivacyTerms.count())
        assertEquals(3, firstTerm.badPrivacyTerms.count())

        val secondTerm = terms.first { it.name == "anotherexample.com" }
        assertEquals(50, secondTerm.score)
        assertEquals("B", secondTerm.classification)
        assertEquals(1, secondTerm.goodPrivacyTerms.count())
        assertEquals(0, secondTerm.badPrivacyTerms.count())
    }

    @Test(expected = JsonDataException::class)
    fun whenFormatIsMismatchedThenExceptionIsThrown() {
        val json = loadText(TermsOfServiceListAdapterTest::class.java.classLoader!!, "json/tosdr_mismatched.json")
        jsonAdapter.fromJson(json)
    }
}
