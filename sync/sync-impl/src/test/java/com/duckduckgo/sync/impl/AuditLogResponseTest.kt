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

package com.duckduckgo.sync.impl

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditLogResponseTest {

    // Same setup as the app's Moshi: no Kotlin reflection adapter, so fields are set by Java reflection.
    private val adapter = Moshi.Builder().build().adapter(AuditLogResponse::class.java)

    @Test
    fun whenVersionIsStringThenVersionParsed() {
        val entry = parse(page(entry(version = "\"1\""))).auditLog.entries.single()

        assertEquals("1", entry.version)
    }

    @Test
    fun whenVersionIsNumberThenVersionNormalisedToString() {
        val entry = parse(page(entry(version = "1"))).auditLog.entries.single()

        assertEquals("1", entry.version)
    }

    @Test
    fun whenVersionIsUnknownStringThenKeptAsIs() {
        val entry = parse(page(entry(version = "\"2\""))).auditLog.entries.single()

        assertEquals("2", entry.version)
    }

    @Test
    fun whenVersionIsFractionalNumberThenVersionIsNull() {
        val entry = parse(page(entry(version = "1.5"))).auditLog.entries.single()

        assertNull(entry.version)
    }

    @Test
    fun whenVersionMissingThenVersionIsNull() {
        val json = page("""{"id":"entry-1","payload":"jwe"}""")

        assertNull(parse(json).auditLog.entries.single().version)
    }

    @Test
    fun whenEntryParsedThenIdAndPayloadSet() {
        val entry = parse(page(entry(version = "\"1\""))).auditLog.entries.single()

        assertEquals("entry-1", entry.id)
        assertEquals("jwe", entry.payload)
    }

    @Test
    fun whenBothCursorsPresentThenBothParsed() {
        val auditLog = parse(page(entry(version = "\"1\""), after = "\"a1\"", before = "\"b1\"")).auditLog

        assertEquals("a1", auditLog.after)
        assertEquals("b1", auditLog.before)
    }

    @Test
    fun whenBeforeMissingThenBeforeIsNull() {
        val auditLog = parse(page(entry(version = "\"1\""), after = "\"a1\"", before = null)).auditLog

        assertEquals("a1", auditLog.after)
        assertNull(auditLog.before)
    }

    @Test
    fun whenNoEntriesThenEmptyListAndAfterStillParsed() {
        val auditLog = parse(page(after = "\"a1\"", before = null)).auditLog

        assertTrue(auditLog.entries.isEmpty())
        assertEquals("a1", auditLog.after)
    }

    @Test
    fun whenSeveralEntriesThenOrderKept() {
        val json = page(
            """{"id":"newest","version":"1","payload":"p1"}""",
            """{"id":"oldest","version":1,"payload":"p2"}""",
        )

        assertEquals(listOf("newest", "oldest"), parse(json).auditLog.entries.map { it.id })
    }

    private fun parse(json: String): AuditLogResponse = adapter.fromJson(json)!!

    private fun entry(version: String): String = """{"id":"entry-1","version":$version,"payload":"jwe"}"""

    private fun page(vararg entries: String, after: String? = "\"a\"", before: String? = "\"b\""): String {
        val cursors = listOfNotNull(after?.let { "\"after\":$it" }, before?.let { "\"before\":$it" })
        val fields = listOf("\"entries\":[${entries.joinToString(",")}]") + cursors
        return """{"audit_log":{${fields.joinToString(",")}}}"""
    }
}
