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

/** Plain-text model attribution for the chat-history export header. */
data class ModelDisplay(
    val fullName: String?,
    val shortName: String,
    val providerPossessive: String?,
)

/** Possessive form of the provider name. Null for providers without a clean possessive (OSS, UNKNOWN). */
val ModelProvider.possessive: String?
    get() = when (this) {
        ModelProvider.OPENAI -> "OpenAI's"
        ModelProvider.ANTHROPIC -> "Anthropic's"
        ModelProvider.META -> "Meta's"
        ModelProvider.MISTRAL -> "Mistral's"
        ModelProvider.OSS, ModelProvider.UNKNOWN -> null
    }

fun AIChatModel.toModelDisplay(): ModelDisplay = ModelDisplay(
    fullName = name,
    shortName = shortName,
    providerPossessive = provider.possessive,
)
