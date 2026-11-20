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

package com.duckduckgo.duckchat.impl.responseready

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingResponseIdTest {

    private fun chat(vararg messages: String) = """{"chatId":"c","messages":[${messages.joinToString(",")}]}"""

    private fun user(extra: String = "") = """{"role":"user","content":"hi"$extra}"""

    private val assistant = """{"role":"assistant","content":"yo","messageId":"x"}"""

    @Test
    fun `when legacy format then top level messageId`() {
        assertEquals("r1", findPendingResponseId(chat(user(""","messageId":"r1","generationTimestamp":1"""))))
    }

    @Test
    fun `when canonical format then meta recovery messageId`() {
        val json = chat(user(""","meta":{"recovery":{"messageId":"r2","generationTimestamp":1}}"""))
        assertEquals("r2", findPendingResponseId(json))
    }

    @Test
    fun `when both present then canonical wins`() {
        val json = chat(user(""","messageId":"old","meta":{"recovery":{"messageId":"new"}}"""))
        assertEquals("new", findPendingResponseId(json))
    }

    @Test
    fun `when last message is assistant then null even if it has messageId`() {
        assertNull(findPendingResponseId(chat(user(""","messageId":"r1""""), assistant)))
    }

    @Test
    fun `when earlier user message has id but last is assistant then null`() {
        assertNull(findPendingResponseId(chat(user(""","messageId":"r1""""), """{"role":"assistant","content":"yo"}""")))
    }

    @Test
    fun `when several user messages then only the last one counts`() {
        val json = chat(user(""","messageId":"r1""""), assistant, user(""","messageId":"r2""""))
        assertEquals("r2", findPendingResponseId(json))
    }

    @Test
    fun `when last user message has no id then null`() {
        assertNull(findPendingResponseId(chat(user())))
    }

    @Test
    fun `when id is blank then null`() {
        assertNull(findPendingResponseId(chat(user(""","messageId":"  ","meta":{"recovery":{"messageId":""}}"""))))
    }

    @Test
    fun `when json is null then null`() {
        assertNull(findPendingResponseId(null))
    }

    @Test
    fun `when json is malformed then null`() {
        assertNull(findPendingResponseId("{not json"))
    }

    @Test
    fun `when no messages then null`() {
        assertNull(findPendingResponseId("""{"chatId":"c"}"""))
        assertNull(findPendingResponseId("""{"chatId":"c","messages":[]}"""))
    }

    @Test
    fun `when messageId is json null then null`() {
        assertNull(findPendingResponseId(chat(user(""","messageId":null"""))))
    }

    @Test
    fun `when canonical id is null then falls back to legacy id`() {
        val json = chat(user(""","messageId":"r1","meta":{"recovery":{"messageId":null}}"""))
        assertEquals("r1", findPendingResponseId(json))
    }

    @Test
    fun `when id is not a string then null`() {
        assertNull(findPendingResponseId(chat(user(""","messageId":123,"meta":{"recovery":{"messageId":{"a":1}}}"""))))
    }
}
