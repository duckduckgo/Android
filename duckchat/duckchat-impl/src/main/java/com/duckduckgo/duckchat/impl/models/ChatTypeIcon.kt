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

import androidx.annotation.DrawableRes
import com.duckduckgo.duckchat.impl.R

@DrawableRes
internal fun ChatType.iconRes(pinned: Boolean): Int = iconRes(
    pinned,
    discussion = R.drawable.ic_chat_24,
    discussionPinned = R.drawable.ic_pin_24,
    image = R.drawable.ic_images_24,
    imagePinned = R.drawable.ic_images_pin_24,
    voice = R.drawable.ic_voice_24,
    voicePinned = R.drawable.ic_voice_pin_24,
)

/** Same mapping as [iconRes] with the `_daynight` copies, since RemoteViews cannot resolve `?attr` colours. */
@DrawableRes
internal fun ChatType.widgetIconRes(pinned: Boolean): Int = iconRes(
    pinned,
    discussion = R.drawable.ic_chat_24_daynight,
    discussionPinned = R.drawable.ic_pin_24_daynight,
    image = R.drawable.ic_images_24_daynight,
    imagePinned = R.drawable.ic_images_pin_24_daynight,
    voice = R.drawable.ic_voice_24_daynight,
    voicePinned = R.drawable.ic_voice_pin_24_daynight,
)

@Suppress("LongParameterList")
private fun ChatType.iconRes(
    pinned: Boolean,
    discussion: Int,
    discussionPinned: Int,
    image: Int,
    imagePinned: Int,
    voice: Int,
    voicePinned: Int,
): Int = when (this) {
    ChatType.Discussion -> if (pinned) discussionPinned else discussion
    ChatType.ImageGeneration -> if (pinned) imagePinned else image
    ChatType.Voice -> if (pinned) voicePinned else voice
}
