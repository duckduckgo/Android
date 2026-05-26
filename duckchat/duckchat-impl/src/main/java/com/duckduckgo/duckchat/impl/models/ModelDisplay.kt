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

package com.duckduckgo.duckchat.impl.models

/**
 * Plain-text rendering of a chat's model attribution, used by the chat-history export header.
 *
 *  - `fullName` is the long label rendered in `using <Provider>'s <fullName> Model`. Null collapses
 *    the header to `using an AI Model`.
 *  - `shortName` is the per-turn prefix (e.g. `GPT-5 mini:`) for Discussion / ImageGeneration outputs.
 *    Always non-null; defaults to the raw model id when nothing better is available.
 *  - `providerPossessive` (e.g. `OpenAI's`) is dropped when null; the header falls back to
 *    `using the <fullName> Model`.
 */
data class ModelDisplay(
    val fullName: String?,
    val shortName: String,
    val providerPossessive: String?,
)

/**
 * Possessive form of the provider name, used by [ModelDisplay.providerPossessive] when the model
 * comes from the live `/duckchat/v1/models` API. Null when there isn't a clean possessive — the
 * export header falls back to `using the <Model> Model` in that case.
 */
val ModelProvider.possessive: String?
    get() = when (this) {
        ModelProvider.OPENAI -> "OpenAI's"
        ModelProvider.ANTHROPIC -> "Anthropic's"
        ModelProvider.META -> "Meta's"
        ModelProvider.MISTRAL -> "Mistral's"
        ModelProvider.OSS, ModelProvider.UNKNOWN -> null
    }

/** Builds the export-header display strings from a live API model. */
fun AIChatModel.toModelDisplay(): ModelDisplay = ModelDisplay(
    fullName = name,
    shortName = shortName,
    providerPossessive = provider.possessive,
)
