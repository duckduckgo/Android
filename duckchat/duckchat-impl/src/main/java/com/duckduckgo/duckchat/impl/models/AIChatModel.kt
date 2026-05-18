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
    @field:Json(name = "supportedFileTypes") val supportedFileTypes: List<String>? = null,
    @field:Json(name = "supportedReasoningEffort") val supportedReasoningEffort: List<String>? = null,
    @field:Json(name = "supportedTools") val supportedTools: List<String>? = null,
)

data class RemoteTierAttachmentLimits(
    val files: RemoteFileLimits? = null,
    val images: RemoteImageLimits? = null,
)

data class RemoteFileLimits(
    val maxPerConversation: Int? = null,
    val maxFileSizeMB: Int? = null,
    val maxTotalFileSizeBytes: Long? = null,
    val maxPagesPerFile: Int? = null,
)

data class RemoteImageLimits(
    val maxPerTurn: Int? = null,
    val maxPerConversation: Int? = null,
    val maxInputCharsWithAttachments: Int? = null,
)

data class AttachmentLimits(
    val files: FileLimits = FileLimits(),
    val images: ImageLimits = ImageLimits(),
)

data class FileLimits(
    val maxPerConversation: Int = DEFAULT_FILE_MAX_PER_CONVERSATION,
    val maxFileSizeBytes: Long = DEFAULT_FILE_MAX_SIZE_BYTES,
    val maxTotalFileSizeBytes: Long = DEFAULT_FILE_MAX_SIZE_BYTES,
    val maxPagesPerFile: Int = DEFAULT_FILE_MAX_PAGES,
) {
    companion object {
        const val DEFAULT_FILE_MAX_PER_CONVERSATION = 3
        const val DEFAULT_FILE_MAX_SIZE_BYTES = 5L * 1024 * 1024
        const val DEFAULT_FILE_MAX_PAGES = 8
    }
}

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
    val fileSizeBytesUsed: Long = 0L,
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
    val supportedFileTypes: List<String> = emptyList(),
    val supportedReasoningEfforts: List<ReasoningEffort> = emptyList(),
    val supportedTools: List<Tool> = emptyList(),
) {
    val supportsFileUpload: Boolean
        get() = supportedFileTypes.isNotEmpty()

    fun supportsTool(tool: Tool): Boolean = supportedTools.contains(tool)

    /** The lowest [UserTier] required to access this model, or `null` if no public tier applies. */
    val requiredTier: UserTier?
        get() = when {
            accessTier.isEmpty() -> UserTier.FREE
            accessTier.contains(UserTier.FREE.rawValue) -> UserTier.FREE
            accessTier.contains(UserTier.PLUS.rawValue) -> UserTier.PLUS
            accessTier.contains(UserTier.PRO.rawValue) -> UserTier.PRO
            else -> null
        }

    companion object {
        val NATIVE_SUPPORTED_IMAGE_FORMATS = listOf("png", "jpeg", "webp")
    }
}

enum class UserTier(val rawValue: String) {
    FREE("free"),
    PLUS("plus"),
    PRO("pro"),
    ;

    companion object {
        fun from(raw: String?): UserTier? = entries.firstOrNull { it.rawValue == raw }
    }
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
