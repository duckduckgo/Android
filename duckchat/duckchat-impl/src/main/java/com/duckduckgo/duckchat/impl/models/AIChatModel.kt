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

import com.squareup.moshi.Json

data class AIChatModelsResponse(
    val models: List<RemoteAIChatModel>,
    val attachmentLimits: Map<String, RemoteTierAttachmentLimits>? = null,
)

data class RemoteAIChatModel(
    val id: String,
    val name: String,
    @field:Json(name = "displayName") val displayName: String? = null,
    @field:Json(name = "modelShortName") val shortName: String? = null,
    @field:Json(name = "accessTier") val accessTier: List<String>? = null,
    @field:Json(name = "entityHasAccess") val entityHasAccess: Boolean = false,
    @field:Json(name = "provider") val provider: String? = null,
    @field:Json(name = "supportsImageUpload") val supportsImageUpload: Boolean = false,
)

data class RemoteTierAttachmentLimits(
    val images: RemoteImageLimits? = null,
)

data class RemoteImageLimits(
    val maxPerTurn: Int? = null,
    val maxPerConversation: Int? = null,
    val maxInputCharsWithAttachments: Int? = null,
)

data class AttachmentLimits(
    val images: ImageLimits = ImageLimits(),
)

data class ImageLimits(
    val maxPerTurn: Int = DEFAULT_IMAGE_MAX_PER_TURN,
    val maxPerConversation: Int = DEFAULT_IMAGE_MAX_PER_CONVERSATION,
    val maxInputCharsWithAttachments: Int = DEFAULT_MAX_INPUT_CHARS_WITH_ATTACHMENTS,
) {
    companion object {
        const val DEFAULT_IMAGE_MAX_PER_TURN = 3
        const val DEFAULT_IMAGE_MAX_PER_CONVERSATION = 5
        const val DEFAULT_MAX_INPUT_CHARS_WITH_ATTACHMENTS = 4500
    }
}

data class AIChatAttachmentUsage(
    val imagesUsed: Int = 0,
    val filesUsed: Int = 0,
    val fileSizeBytesUsed: Int = 0,
)

data class AIChatModel(
    val id: String,
    val name: String,
    val displayName: String,
    val shortName: String,
    val accessTier: List<String>,
    val isAccessible: Boolean,
    val provider: ModelProvider = ModelProvider.UNKNOWN,
    val supportsImageUpload: Boolean = false,
    val supportedImageFormats: List<String> = emptyList(),
) {
    companion object {
        val NATIVE_SUPPORTED_IMAGE_FORMATS = listOf("png", "jpeg", "webp")
    }
}

enum class UserTier(val rawValue: String) {
    FREE("free"),
    PLUS("plus"),
    PRO("pro"),
}

enum class ModelProvider {
    OPENAI,
    META,
    ANTHROPIC,
    MISTRAL,
    OSS,
    UNKNOWN,
    ;

    companion object {
        fun from(id: String, providerString: String?): ModelProvider {
            return when {
                id.startsWith("meta-llama/") || id.startsWith("meta-llama_") || providerString == "azure" -> META
                id.startsWith("mistralai/") || id.startsWith("mistralai_") -> MISTRAL
                id.contains("gpt-oss") -> OSS
                providerString == "anthropic" -> ANTHROPIC
                providerString == "openai" -> OPENAI
                else -> UNKNOWN
            }
        }
    }
}
