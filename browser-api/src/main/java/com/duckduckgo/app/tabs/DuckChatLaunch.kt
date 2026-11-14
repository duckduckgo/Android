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
 * How Duck.ai is launched.
 */
sealed interface DuckChatLaunch {
    /**
     * Opens `duckChatUrl`, or a new chat when it is null.
     *
     * @param origin where the launch comes from.
     */
    data class Open(val origin: DuckChatLaunchOrigin = DuckChatLaunchOrigin.IN_APP) : DuckChatLaunch

    /**
     * Opens a new tab whose native input starts in image generation mode.
     *
     * @param origin where the launch comes from.
     */
    data class ImageGeneration(val origin: DuckChatLaunchOrigin = DuckChatLaunchOrigin.IN_APP) : DuckChatLaunch

    /**
     * Opens a new tab whose native input starts the camera capture.
     *
     * @param origin where the launch comes from.
     */
    data class CameraCapture(val origin: DuckChatLaunchOrigin = DuckChatLaunchOrigin.IN_APP) : DuckChatLaunch

    /**
     * Opens a new Duck.ai voice chat.
     *
     * @param origin where the launch comes from.
     */
    data class Voice(val origin: DuckChatLaunchOrigin = DuckChatLaunchOrigin.IN_APP) : DuckChatLaunch

    /**
     * Opens the native chat history screen.
     *
     * @param origin where the launch comes from.
     */
    data class ChatHistory(val origin: DuckChatLaunchOrigin) : DuckChatLaunch

    /**
     * Opens the contextual Duck.ai sheet on the current tab, optionally with the text selected in the page.
     */
    data class ContextualChat(val textSelection: String?) : DuckChatLaunch

    /**
     * Opens a new full screen Duck.ai tab, in regular mode, with [text] selected in another app.
     */
    data class TextSelection(val text: String) : DuckChatLaunch
}
