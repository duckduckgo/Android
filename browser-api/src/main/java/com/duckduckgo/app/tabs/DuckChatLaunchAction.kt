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

package com.duckduckgo.app.tabs

/**
 * What happens once Duck.ai opens.
 */
sealed interface DuckChatLaunchAction {
    /**
     * The new tab's native input starts in image generation mode.
     */
    data object ImageGeneration : DuckChatLaunchAction

    /**
     * The new tab's native input starts the camera capture.
     */
    data object CameraCapture : DuckChatLaunchAction

    /**
     * Opens the contextual Duck.ai sheet on the current tab, optionally with the text selected in the page.
     */
    data class ContextualChat(val textSelection: String?) : DuckChatLaunchAction

    /**
     * Opens a new full screen Duck.ai tab with [text] selected in another app.
     */
    data class TextSelection(val text: String) : DuckChatLaunchAction
}
