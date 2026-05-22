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

package com.duckduckgo.duckchat.impl.history

import com.duckduckgo.duckchat.impl.models.ChatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ChatExporterTest {

    // ZoneId Europe/Paris matches the device that produced the spec's export-example.txt
    // (UTC+2 in May 2026 → 14:23:15 UTC reads as 4:23:15 PM local).
    private val exporter = ChatExporter(ZoneId.of("Europe/Paris"))

    @Test
    fun `Discussion single turn matches the cross-platform reference output`() {
        val result = exporter.export(SPEC_CHAT_JSON, ChatType.Discussion)

        val expected = """
            This conversation was generated with Duck.ai (https://duck.ai) using OpenAI's GPT-5 mini Model. AI chats may display inaccurate or offensive information (see https://duckduckgo.com/duckai/privacy-terms for more info).

            ====================

            User prompt 1 of 1 - 5/15/2026, 4:23:15 PM:
            cat

            GPT-5 mini:
            Do you want a picture, facts, care tips, behavior explanation, name ideas, or something else about cats?
        """.trimIndent()

        assertTrue(result is ExportResult.Text)
        assertEquals(expected, result.content)
    }

    @Test
    fun `multi-turn output inserts the turn separator between turns`() {
        val result = exporter.export(TWO_TURN_JSON, ChatType.Discussion)

        assertTrue(result is ExportResult.Text)
        val output = result.content
        assertTrue("contains turn 1 numbering", output.contains("User prompt 1 of 2 - 5/15/2026, 4:00:00 PM:"))
        assertTrue("contains turn 2 numbering", output.contains("User prompt 2 of 2 - 5/15/2026, 4:01:00 PM:"))
        assertTrue("turn separator sits between turns", output.contains("Hello!\n\n--------------------\n\nUser prompt 2"))
    }

    @Test
    fun `Voice assistant prefix is the literal Voice Chat label`() {
        val result = exporter.export(TWO_TURN_JSON, ChatType.Voice)

        assertTrue(result is ExportResult.Text)
        val output = result.content
        assertTrue("uses literal Voice Chat prefix", output.contains("Voice Chat:\nHello!"))
        assertFalse("does not use the model name as prefix", output.contains("GPT-5 mini:"))
    }

    @Test
    fun `Voice skips the assistant block entirely when there is no model response`() {
        val json = """
            {
              "model": "voice-mode",
              "messages": [
                {"role":"user","createdAt":"2026-05-15T14:00:00.000Z","content":"hi"},
                {"role":"user","createdAt":"2026-05-15T14:01:00.000Z","content":"bye"},
                {"role":"assistant","createdAt":"2026-05-15T14:01:01.000Z","content":"","parts":[{"type":"text","text":"Goodbye!"}]}
              ]
            }
        """.trimIndent()

        val result = exporter.export(json, ChatType.Voice)

        assertTrue(result is ExportResult.Text)
        val output = result.content
        // First turn (user-only) should run straight into the turn separator with no Voice Chat block.
        assertTrue("user-only turn has no assistant block", output.contains("hi\n\n--------------------"))
        // Second turn still renders the Voice Chat response.
        assertTrue("responded turn keeps Voice Chat block", output.contains("Voice Chat:\nGoodbye!"))
    }

    @Test
    fun `ImageGeneration replaces assistant text with image placeholder and consumes fileRefs positionally`() {
        val result = exporter.export(TWO_TURN_JSON, ChatType.ImageGeneration, listOf("uuid-1", "uuid-2"))

        assertTrue(result is ExportResult.Zip)
        result as ExportResult.Zip
        assertEquals(listOf("uuid-1", "uuid-2"), result.imageFileRefs)
        assertTrue(
            "turn 1 carries the image-1 placeholder",
            result.content.contains("GPT-5 mini:\n\n[Generated image: image-1.jpeg]"),
        )
        assertTrue(
            "turn 2 carries the image-2 placeholder",
            result.content.contains("GPT-5 mini:\n\n[Generated image: image-2.jpeg]"),
        )
        // The model's text response is replaced — original text MUST NOT appear in the output.
        assertFalse(result.content.contains("Hello!"))
        assertFalse(result.content.contains("Goodbye!"))
    }

    @Test
    fun `ImageGeneration handles missing fileRefs gracefully`() {
        val result = exporter.export(TWO_TURN_JSON, ChatType.ImageGeneration, fileRefs = emptyList())

        assertTrue(result is ExportResult.Zip)
        result as ExportResult.Zip
        assertTrue("no fileRefs consumed", result.imageFileRefs.isEmpty())
        // With no fileRefs, the model header is still emitted but no placeholder line follows.
        assertTrue(result.content.contains("GPT-5 mini:"))
        assertFalse(result.content.contains("[Generated image:"))
    }

    @Test
    fun `assistant text falls back to content when parts array is absent`() {
        val json = """
            {
              "model": "gpt-5-mini",
              "messages": [
                {"role":"user","createdAt":"2026-05-15T14:00:00.000Z","content":"hello"},
                {"role":"assistant","createdAt":"2026-05-15T14:00:01.000Z","content":"plain-content fallback"}
              ]
            }
        """.trimIndent()

        val result = exporter.export(json, ChatType.Discussion)

        assertTrue(result is ExportResult.Text)
        assertTrue(result.content.contains("GPT-5 mini:\nplain-content fallback"))
    }

    @Test
    fun `reasoning parts are ignored - only text parts are included`() {
        val json = """
            {
              "model": "gpt-5-mini",
              "messages": [
                {"role":"user","createdAt":"2026-05-15T14:00:00.000Z","content":"hi"},
                {"role":"assistant","content":"","parts":[
                  {"type":"reasoning","encryptedText":"secret"},
                  {"type":"text","text":"only this is visible"}
                ]}
              ]
            }
        """.trimIndent()

        val result = exporter.export(json, ChatType.Discussion)

        assertTrue(result is ExportResult.Text)
        assertTrue(result.content.contains("only this is visible"))
        assertFalse("reasoning text is omitted", result.content.contains("secret"))
    }

    @Test
    fun `unknown model falls back to raw id and generic provider wording`() {
        val json = """
            {
              "model": "some-future-model-v2",
              "messages": [
                {"role":"user","createdAt":"2026-05-15T14:00:00.000Z","content":"hi"},
                {"role":"assistant","content":"hello"}
              ]
            }
        """.trimIndent()

        val result = exporter.export(json, ChatType.Discussion)

        assertTrue(result is ExportResult.Text)
        assertTrue("header uses raw id", result.content.contains("using the some-future-model-v2 Model"))
        assertTrue("per-turn label uses raw id", result.content.contains("some-future-model-v2:"))
    }

    @Test
    fun `empty messages array yields header and separator only`() {
        val json = """{"model":"gpt-5-mini","messages":[]}"""

        val result = exporter.export(json, ChatType.Discussion)

        assertTrue(result is ExportResult.Text)
        assertTrue(result.content.startsWith("This conversation was generated"))
        assertTrue(result.content.endsWith("===================="))
    }

    private companion object {
        // Verbatim from specs/click-access-chat-history/chat-example.json (encryptedText abbreviated).
        const val SPEC_CHAT_JSON = """{
  "title" : "cat name",
  "model" : "gpt-5-mini",
  "messages" : [ {
    "createdAt" : "2026-05-15T14:23:15.150Z",
    "content" : "cat",
    "role" : "user",
    "messageId" : "26030492-4a59-47f9-b860-80e8716e9d4a",
    "generationTimestamp" : 1778854995150
  }, {
    "role" : "assistant",
    "createdAt" : "2026-05-15T14:23:15.244Z",
    "content" : "",
    "parts" : [ {
      "type" : "reasoning",
      "id" : "rs_abc",
      "state" : "done",
      "summaryText" : [ ],
      "encryptedText" : "redacted"
    }, {
      "type" : "text",
      "text" : "Do you want a picture, facts, care tips, behavior explanation, name ideas, or something else about cats?"
    } ],
    "status" : "active",
    "model" : "gpt-5-mini",
    "origin" : "text"
  } ],
  "chatId" : "52386ba8-7a9d-4307-950e-05cd7d74917a",
  "lastEdit" : "2026-05-15T14:23:16.313Z",
  "lastEditType" : "user",
  "pinned" : true,
  "pendingSync" : true
}"""

        const val TWO_TURN_JSON = """
            {
              "model": "gpt-5-mini",
              "messages": [
                {"role":"user","createdAt":"2026-05-15T14:00:00.000Z","content":"hi"},
                {"role":"assistant","createdAt":"2026-05-15T14:00:01.000Z","content":"","parts":[{"type":"text","text":"Hello!"}]},
                {"role":"user","createdAt":"2026-05-15T14:01:00.000Z","content":"bye"},
                {"role":"assistant","createdAt":"2026-05-15T14:01:01.000Z","content":"","parts":[{"type":"text","text":"Goodbye!"}]}
              ]
            }
        """
    }
}
