/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel

enum class RemoteMessagingPixelName(override val pixelName: String) : Pixel.PixelName {

    REMOTE_MESSAGE_DISMISSED("m_remote_message_dismissed"),
    REMOTE_MESSAGE_SHOWN("m_remote_message_shown"),
    REMOTE_MESSAGE_SHOWN_UNIQUE("m_remote_message_shown_unique"),
    REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED("m_remote_message_primary_action_clicked"),
    REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED("m_remote_message_secondary_action_clicked"),
    REMOTE_MESSAGE_ACTION_CLICKED("m_remote_message_action_clicked"),
    REMOTE_MESSAGE_SHARED("m_remote_message_share"),
    REMOTE_MESSAGE_IMAGE_LOAD_SUCCESS("m_remote_message_image_load_success"),
    REMOTE_MESSAGE_IMAGE_LOAD_FAILED("m_remote_message_image_load_failed"),
}
