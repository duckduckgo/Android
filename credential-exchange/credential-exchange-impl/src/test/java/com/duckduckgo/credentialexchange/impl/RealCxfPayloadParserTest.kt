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

package com.duckduckgo.credentialexchange.impl

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealCxfPayloadParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = RealCxfPayloadParser(coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenPayloadIsFromGooglePasswordManagerThenAllBasicAuthCredentialsParsed() = runTest {
        val result = testee.parse(GOOGLE_PAYLOAD) as CxfParseResult.Parsed

        assertEquals(2, result.credentials.size)
        with(result.credentials.first()) {
            assertEquals("https://example.com/", url)
            assertEquals("user@example.com", username)
            assertEquals("pass-one", password)
            assertEquals("https://example.com/", title)
            assertEquals("a note", note)
        }
    }

    @Test
    fun whenCredentialTypeIsNotBasicAuthThenIgnored() = runTest {
        val result = testee.parse(GOOGLE_PAYLOAD) as CxfParseResult.Parsed

        assertTrue(result.credentials.none { it.username == "should-be-ignored" })
    }

    @Test
    fun whenItemHoldsSeveralLoginsThenItsNoteIsNotCopiedOntoEachOne() = runTest {
        val json = payloadWithCredentials(
            """{"type":"basic-auth","username":{"value":"one"},"password":{"value":"p"}},
            {"type":"basic-auth","username":{"value":"two"},"password":{"value":"p"}},
            {"type":"note","content":{"value":"shared note"}}""",
        )

        val result = testee.parse(json) as CxfParseResult.Parsed

        assertEquals(2, result.credentials.size)
        assertTrue(result.credentials.all { it.note == null })
    }

    @Test
    fun whenPasswordMissingThenCredentialSkipped() = runTest {
        val json = payloadWithCredentials("""{"type":"basic-auth","username":{"value":"no-password"}}""")

        val result = testee.parse(json) as CxfParseResult.Parsed

        assertTrue(result.credentials.isEmpty())
    }

    @Test
    fun whenScopeMissingThenUrlIsNull() = runTest {
        val json = """
            {"accounts":[{"items":[{"title":"t","credentials":[
            {"type":"basic-auth","username":{"value":"u"},"password":{"value":"p"}}]}]}]}
        """.trimIndent()

        val result = testee.parse(json) as CxfParseResult.Parsed

        assertNull(result.credentials.first().url)
    }

    @Test
    fun whenAccountsMissingThenMalformed() = runTest {
        assertTrue(testee.parse("""{"version":{"major":1,"minor":0}}""") is CxfParseResult.Malformed)
    }

    @Test
    fun whenJsonIsNotValidThenMalformed() = runTest {
        assertTrue(testee.parse("not json") is CxfParseResult.Malformed)
    }

    private fun payloadWithCredentials(credentials: String): String =
        """{"accounts":[{"items":[{"title":"t","credentials":[$credentials]}]}]}"""

    companion object {
        private val GOOGLE_PAYLOAD = """
            {"version":{"major":1,"minor":0},"exporterRpId":"passwords.google.com",
            "exporterDisplayName":"Google Password Manager","timestamp":1790167734,
            "accounts":[{"id":"abc","username":"","email":"someone@example.com","collections":[],
            "items":[
            {"id":"i1","creationAt":1727711439,"modifiedAt":1753116843,"title":"https://example.com/",
            "favorite":false,"scope":{"urls":["https://example.com/"],"androidApps":[]},
            "credentials":[
            {"type":"basic-auth","username":{"fieldType":"string","value":"user@example.com"},
            "password":{"fieldType":"concealed-string","value":"pass-one"}},
            {"type":"note","content":{"fieldType":"string","value":"a note"}}]},
            {"id":"i2","creationAt":1727711289,"title":"https://other.example/","favorite":false,
            "scope":{"urls":["https://other.example/"],"androidApps":[]},
            "credentials":[
            {"type":"passkey","username":{"fieldType":"string","value":"should-be-ignored"}},
            {"type":"basic-auth","username":{"fieldType":"string","value":"second-user"},
            "password":{"fieldType":"concealed-string","value":"pass-two"}}]}]}]}
        """.trimIndent()
    }
}
