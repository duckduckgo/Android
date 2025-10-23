/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealTakeoutWebMessageParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var parser: TakeoutWebMessageParser

    @Before
    fun setUp() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        parser = RealTakeoutWebMessageParser(
            dispatchers = coroutineTestRule.testDispatcherProvider,
            moshi = moshi,
        )
    }

    @Test
    fun whenParsingSuccessfulActionMessageThenReturnsSuccess() = runTest {
        val result = parser.parseMessage("success_with_action_type".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionSuccess)
        val success = result as TakeoutMessageResult.TakeoutActionSuccess
        assertEquals("manage-button-click", success.actionID)
    }

    @Test
    fun whenParsingErrorActionMessageThenReturnsError() = runTest {
        val result = parser.parseMessage("error_with_action_id".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionError)
        val error = result as TakeoutMessageResult.TakeoutActionError
        assertEquals("failed-action", error.actionID)
    }

    @Test
    fun whenParsingErrorWithoutActionIDThenReturnsErrorWithNullActionID() = runTest {
        val result = parser.parseMessage("error_without_action_id".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionError)
        val error = result as TakeoutMessageResult.TakeoutActionError
        assertEquals(null, error.actionID)
    }

    @Test
    fun whenParsingUnexpectedValidJsonThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("invalid_structure".readFile())

        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingMalformedJsonThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("malformed_json".readFile())

        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingEmptyResultThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("empty_result".readFile())

        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingMessageWithoutDataFieldThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("missing_data_field".readFile())
        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingMessageWithoutResultFieldThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("missing_result_field".readFile())
        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingSuccessWithMissingActionIDThenReturnsError() = runTest {
        val result = parser.parseMessage("success_missing_action_id".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionError)
        val error = result as TakeoutMessageResult.TakeoutActionError
        assertEquals(null, error.actionID)
    }

    @Test
    fun whenParsingSuccessWithMissingActionTypeThenStillSucceeds() = runTest {
        val result = parser.parseMessage("success_missing_action_type".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionSuccess)
        val success = result as TakeoutMessageResult.TakeoutActionSuccess
        assertEquals("test-action", success.actionID)
    }

    @Test
    fun whenParsingCompletelyEmptySuccessObjectThenReturnsError() = runTest {
        val result = parser.parseMessage("success_empty_object".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionError)
        val error = result as TakeoutMessageResult.TakeoutActionError
        assertEquals(null, error.actionID)
    }

    @Test
    fun whenParsingCompletelyEmptyErrorObjectThenGracefullyHandles() = runTest {
        val result = parser.parseMessage("error_empty_object".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionError)
        val error = result as TakeoutMessageResult.TakeoutActionError
        assertEquals(null, error.actionID)
    }

    @Test
    fun whenParsingUnexpectedStructureThenReturnsUnknownFormat() = runTest {
        val result = parser.parseMessage("unexpected_structure".readFile())

        assertTrue(result is TakeoutMessageResult.UnknownMessageFormat)
    }

    @Test
    fun whenParsingWithExtraFieldsThenIgnoresThemGracefully() = runTest {
        val result = parser.parseMessage("success_with_extra_fields".readFile())

        assertTrue(result is TakeoutMessageResult.TakeoutActionSuccess)
        val success = result as TakeoutMessageResult.TakeoutActionSuccess
        assertEquals("test-action", success.actionID)
    }

    private fun String.readFile(): String {
        return FileUtilities.loadText(
            RealTakeoutWebMessageParserTest::class.java.classLoader!!,
            "json/takeout/$this.json",
        )
    }
}
