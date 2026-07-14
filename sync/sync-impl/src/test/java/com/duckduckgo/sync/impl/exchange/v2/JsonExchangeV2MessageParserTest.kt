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

package com.duckduckgo.sync.impl.exchange.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonExchangeV2MessageParserTest {

    private val parser = JsonExchangeV2MessageParser()

    @Test fun `parses hello with channel_id+public_key+version`() {
        val json = """{"type":"hello","channel_id":"abc","public_key":"key","version":"2.1"}"""

        val parsed = parser.parse(json) as ExchangeV2Message.Hello

        assertEquals("abc", parsed.channelId)
        assertEquals("key", parsed.publicKey)
        assertEquals("2.1", parsed.version)
        assertEquals(json, parsed.rawJson)
    }

    @Test fun `hello defaults version to 2 when omitted`() {
        val parsed = parser.parse("""{"type":"hello"}""") as ExchangeV2Message.Hello
        assertEquals("2", parsed.version)
    }

    @Test fun `parses recovery_code_available with user_id+name+kind`() {
        val json = """{"type":"recovery_code_available","user_id":"u","name":"Alice","kind":"ddg"}"""

        val parsed = parser.parse(json) as ExchangeV2Message.RecoveryCodeAvailable

        assertEquals("u", parsed.userId)
        assertEquals("Alice", parsed.name)
        assertEquals("ddg", parsed.kind)
    }

    @Test fun `parses recovery_code_request without user_id`() {
        val json = """{"type":"recovery_code_request","name":"Bob","kind":"3party"}"""

        val parsed = parser.parse(json) as ExchangeV2Message.RecoveryCodeRequest

        assertEquals("Bob", parsed.name)
        assertEquals("3party", parsed.kind)
    }

    @Test fun `parses recovery_code_response with embedded recovery_code`() {
        val json = """{"type":"recovery_code_response","recovery_code":"the-code"}"""

        val parsed = parser.parse(json) as ExchangeV2Message.RecoveryCodeResponse

        assertEquals("the-code", parsed.recoveryCode)
    }

    @Test fun `parses bodyless types awaiting_confirmation confirmed denied unavailable`() {
        assertTrue(parser.parse("""{"type":"recovery_code_awaiting_confirmation"}""") is ExchangeV2Message.RecoveryCodeAwaitingConfirmation)
        assertTrue(parser.parse("""{"type":"recovery_code_confirmed"}""") is ExchangeV2Message.RecoveryCodeConfirmed)
        assertTrue(parser.parse("""{"type":"recovery_code_denied"}""") is ExchangeV2Message.RecoveryCodeDenied)
        assertTrue(parser.parse("""{"type":"recovery_code_unavailable"}""") is ExchangeV2Message.RecoveryCodeUnavailable)
    }

    @Test fun `unknown type becomes Unknown with type preserved (forward-compat)`() {
        val json = """{"type":"future_message_v3","extra":"data"}"""

        val parsed = parser.parse(json) as ExchangeV2Message.Unknown

        assertEquals("future_message_v3", parsed.messageType)
        assertEquals(json, parsed.rawJson)
    }

    @Test fun `missing type field is treated as Unknown with empty messageType`() {
        val parsed = parser.parse("""{"no":"type"}""") as ExchangeV2Message.Unknown
        assertEquals("", parsed.messageType)
    }

    @Test fun `malformed JSON becomes Unknown rather than throwing`() {
        val parsed = parser.parse("{not valid json") as ExchangeV2Message.Unknown
        assertEquals("", parsed.messageType)
    }

    @Test fun `missing fields in a typed message default to empty string`() {
        // Defensive against partial peers — we should never NPE on missing fields.
        val parsed = parser.parse("""{"type":"recovery_code_available"}""") as ExchangeV2Message.RecoveryCodeAvailable

        assertEquals("", parsed.userId)
        assertEquals("", parsed.name)
        assertEquals("", parsed.kind)
    }
}
