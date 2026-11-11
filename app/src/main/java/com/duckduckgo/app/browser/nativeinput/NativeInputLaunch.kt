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

package com.duckduckgo.app.browser.nativeinput

import com.duckduckgo.app.tabs.DuckChatLaunch

/**
 * Launch actions a new Duck.ai tab's native input can honour the first time it shows.
 * Serialized by name across process restarts, so entries must not be renamed.
 */
enum class NativeInputLaunch {
    IMAGE_GENERATION,
    CAMERA_CAPTURE,
    ;

    fun toDuckChatLaunch(): DuckChatLaunch = when (this) {
        IMAGE_GENERATION -> DuckChatLaunch.ImageGeneration()
        CAMERA_CAPTURE -> DuckChatLaunch.CameraCapture()
    }

    companion object {
        fun from(launch: DuckChatLaunch): NativeInputLaunch? = when (launch) {
            is DuckChatLaunch.ImageGeneration -> IMAGE_GENERATION
            is DuckChatLaunch.CameraCapture -> CAMERA_CAPTURE
            else -> null
        }

        fun fromName(name: String?): NativeInputLaunch? = name?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
