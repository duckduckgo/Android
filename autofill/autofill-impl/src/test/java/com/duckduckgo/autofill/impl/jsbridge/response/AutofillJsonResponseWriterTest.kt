/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.jsbridge.response

import com.duckduckgo.autofill.impl.domain.javascript.JavascriptCredentials
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillJsonResponseWriterTest {
    private val testee = AutofillJsonResponseWriter(Moshi.Builder().build())

    @Test
    fun whenGenerateResponseGetAutofillDataTheReturnAutofillDataJson() {
        val expectedJson = "{\n" +
            "  \"success\": {\n" +
            "    \"action\": \"fill\",\n" +
            "    \"credentials\": {\n" +
            "      \"password\": \"password\",\n" +
            "      \"username\": \"test\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"type\": \"getAutofillDataResponse\"\n" +
            "}"
        assertEquals(
            expectedJson,
            testee.generateResponseGetAutofillData(
                JavascriptCredentials(
                    username = "test",
                    password = "password",
                ),
            ),
        )
    }

    @Test
    fun whenGenerateEmptyResponseGetAutofillDataThenReturnEmptyResponseJson() {
        val expectedJson = "{\n" +
            "  \"success\": {\n" +
            "    \"action\": \"none\"\n" +
            "  },\n" +
            "  \"type\": \"getAutofillDataResponse\"\n" +
            "}"
        assertEquals(
            expectedJson,
            testee.generateEmptyResponseGetAutofillData(),
        )
    }
}
