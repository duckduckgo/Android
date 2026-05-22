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
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a Duck.ai chat (FE-owned JSON blob) into the platform-standard plain-text shape.
 * Pure — no I/O, no DI. Inject a [ZoneId] to keep tests deterministic across machine timezones.
 *
 * Output shape varies by [ChatType] (see research.md R-16):
 *  - [ChatType.Discussion]: assistant turns prefixed with `<Model>:`.
 *  - [ChatType.Voice]: assistant turns prefixed with literal "Voice Chat:" and omitted entirely
 *    when the model produced no text (matches the macOS/Windows reference voice format where
 *    user-only turns appear without a response block).
 *  - [ChatType.ImageGeneration]: assistant text replaced by `[Generated image: image-N.jpeg]`;
 *    the resulting [ExportResult.Zip] carries the consumed fileRefs in turn order. Positional
 *    fileRef ↔ assistant-turn association is a known assumption — see R-16 open question (1).
 */
internal class ChatExporter(private val zoneId: ZoneId = ZoneId.systemDefault()) {

    fun export(
        rawJson: String,
        chatType: ChatType = ChatType.Discussion,
        fileRefs: List<String> = emptyList(),
    ): ExportResult {
        val json = JSONObject(rawJson)
        val display = ModelDisplay.from(json.optString("model"))
        val turns = extractTurns(json.optJSONArray("messages"))

        return when (chatType) {
            ChatType.Discussion -> ExportResult.Text(renderDiscussion(display, turns))
            ChatType.Voice -> ExportResult.Text(renderVoice(display, turns))
            ChatType.ImageGeneration -> renderImage(display, turns, fileRefs)
        }
    }

    private fun renderDiscussion(display: ModelDisplay, turns: List<Turn>): String =
        buildContent(display, turns) { _, turn -> "${display.shortName}:\n${turn.assistantText}" }

    private fun renderVoice(display: ModelDisplay, turns: List<Turn>): String =
        buildContent(display, turns) { _, turn ->
            if (turn.assistantText.isBlank()) "" else "Voice Chat:\n${turn.assistantText}"
        }

    private fun renderImage(
        display: ModelDisplay,
        turns: List<Turn>,
        fileRefs: List<String>,
    ): ExportResult.Zip {
        val consumed = mutableListOf<String>()
        val text = buildContent(display, turns) { index, _ ->
            val uuid = fileRefs.getOrNull(index)
            if (uuid != null) {
                consumed += uuid
                "${display.shortName}:\n\n[Generated image: image-${consumed.size}.jpeg]"
            } else {
                "${display.shortName}:"
            }
        }
        return ExportResult.Zip(text, consumed)
    }

    private fun buildContent(
        display: ModelDisplay,
        turns: List<Turn>,
        assistantBlock: (Int, Turn) -> String,
    ): String = buildString {
        appendLine(header(display))
        appendLine()
        append(SEPARATOR)
        turns.forEachIndexed { index, turn ->
            if (index > 0) {
                appendLine()
                appendLine()
                appendLine(TURN_SEPARATOR)
            } else {
                appendLine()
            }
            appendLine()
            appendLine("User prompt ${index + 1} of ${turns.size} - ${formatTimestamp(turn.createdAt)}:")
            append(turn.userText)
            val assistant = assistantBlock(index, turn)
            if (assistant.isNotEmpty()) {
                appendLine()
                appendLine()
                append(assistant)
            }
        }
    }

    private fun extractTurns(messages: org.json.JSONArray?): List<Turn> {
        if (messages == null || messages.length() == 0) return emptyList()
        val turns = mutableListOf<Turn>()
        var i = 0
        while (i < messages.length()) {
            val msg = messages.getJSONObject(i)
            if (msg.optString("role") == "user") {
                val createdAt = msg.optString("createdAt")
                val userText = msg.optString("content")
                val nextIsAssistant = i + 1 < messages.length() &&
                    messages.getJSONObject(i + 1).optString("role") == "assistant"
                val assistantText = if (nextIsAssistant) extractAssistantText(messages.getJSONObject(i + 1)) else ""
                turns += Turn(createdAt, userText, assistantText)
                i += if (nextIsAssistant) 2 else 1
            } else {
                i++
            }
        }
        return turns
    }

    private fun extractAssistantText(assistantMsg: JSONObject): String {
        val parts = assistantMsg.optJSONArray("parts")
        if (parts == null || parts.length() == 0) return assistantMsg.optString("content")
        val textParts = buildList {
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.optString("type") == "text") add(part.optString("text"))
            }
        }
        return if (textParts.isNotEmpty()) textParts.joinToString(separator = "\n") else assistantMsg.optString("content")
    }

    private fun formatTimestamp(iso: String): String {
        val instant = runCatching { Instant.parse(iso) }.getOrElse { return iso }
        return TIMESTAMP_FORMATTER.withZone(zoneId).format(instant)
    }

    private fun header(display: ModelDisplay): String {
        val using = when {
            display.providerPossessive != null -> "using ${display.providerPossessive} ${display.fullName} Model"
            display.fullName != null -> "using the ${display.fullName} Model"
            else -> "using an AI Model"
        }
        return "This conversation was generated with Duck.ai (https://duck.ai) $using. " +
            "AI chats may display inaccurate or offensive information " +
            "(see https://duckduckgo.com/duckai/privacy-terms for more info)."
    }

    private data class Turn(
        val createdAt: String,
        val userText: String,
        val assistantText: String,
    )

    private companion object {
        const val SEPARATOR = "===================="
        const val TURN_SEPARATOR = "--------------------"
        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a", Locale.US)
    }
}

internal sealed interface ExportResult {
    val content: String

    data class Text(override val content: String) : ExportResult
    data class Zip(override val content: String, val imageFileRefs: List<String>) : ExportResult
}

internal data class ModelDisplay(
    val fullName: String?,
    val shortName: String,
    val providerPossessive: String?,
) {
    companion object {
        fun from(modelId: String): ModelDisplay = TABLE[modelId] ?: ModelDisplay(
            fullName = modelId.takeIf { it.isNotBlank() },
            shortName = modelId.takeIf { it.isNotBlank() } ?: "AI",
            providerPossessive = null,
        )

        private val TABLE: Map<String, ModelDisplay> = mapOf(
            "gpt-5-mini" to ModelDisplay("GPT-5 mini", "GPT-5 mini", "OpenAI's"),
            "gpt-4o" to ModelDisplay("GPT-4o", "GPT-4o", "OpenAI's"),
            "gpt-4o-mini" to ModelDisplay("GPT-4o mini", "GPT-4o mini", "OpenAI's"),
            "claude-3-5-sonnet-latest" to ModelDisplay("Claude 3.5 Sonnet", "Claude 3.5 Sonnet", "Anthropic's"),
            "meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo" to ModelDisplay("Llama 3.1 70B", "Llama 3.1 70B", "Meta's"),
            "mistralai/Mixtral-8x7B-Instruct-v0.1" to ModelDisplay("Mixtral 8x7B", "Mixtral 8x7B", "Mistral's"),
        )
    }
}
