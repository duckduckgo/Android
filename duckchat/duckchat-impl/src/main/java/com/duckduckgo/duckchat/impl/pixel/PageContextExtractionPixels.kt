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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import javax.inject.Inject

class PageContextExtractionPixels @Inject constructor(private val pixel: Pixel) {
    enum class ContextType(val value: String) { PDF("pdf"), MARKDOWN("markdown") }
    enum class Trigger(val value: String) { AUTO("auto"), NAVIGATION("navigation"), USER_REQUEST("user_request"), TAB_CONTENT("tab_content") }
    enum class Reason(val value: String) {
        EMPTY_CONTENT("empty_content"), DESERIALIZE_FAILED("deserialize_failed"), SCRIPT_ERROR("script_error"), TIMEOUT("timeout"),
        NO_WEBVIEW("no_webview"), POST_FAILED("post_failed"), TAB_EVICTED("tab_evicted"), DOCUMENT_UNAVAILABLE("document_unavailable"),
    }
    enum class Category(val value: String) {
        PDF("pdf"), IMAGE("image"), VIDEO("video"), AUDIO("audio"), INTERNAL_PAGE("internalPage"),
        DOCUMENT_TOO_LARGE("documentTooLarge"), LOCAL_FILE("localFile"),
    }

    fun success(contextType: ContextType) {
        fire("m_aichat_page_context_extraction_success", mapOf("context_type" to contextType.value))
    }

    fun failed(reason: Reason, trigger: Trigger, contextType: ContextType, latencyMillis: Long? = null) {
        val parameters = mutableMapOf("reason" to reason.value, "trigger" to trigger.value, "context_type" to contextType.value)
        if (latencyMillis != null) {
            parameters["latency"] = when {
                latencyMillis < 1000 -> "under_1s"
                latencyMillis <= 5000 -> "1_to_5s"
                else -> "over_5s"
            }
        }
        fire("m_aichat_page_context_extraction_failed", parameters)
    }

    fun prevented(category: Category, trigger: Trigger) {
        fire(
            "m_aichat_page_context_extraction_prevented",
            mapOf(
                "reason" to "non_attachable",
                "trigger" to trigger.value,
                "category" to category.value,
            ),
        )
    }

    private fun fire(name: String, parameters: Map<String, String>) {
        pixel.fire(pixelName = "${name}_c", parameters = parameters)
        pixel.fire(pixelName = "${name}_d", parameters = parameters, type = Pixel.PixelType.Daily())
    }
}
