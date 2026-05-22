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
internal fun ChatType.iconRes(pinned: Boolean): Int = when (this) {
    ChatType.Discussion -> if (pinned) R.drawable.ic_chat_pin_24 else R.drawable.ic_chat_24
    ChatType.ImageGeneration -> if (pinned) R.drawable.ic_images_pin_24 else R.drawable.ic_images_24
    ChatType.Voice -> if (pinned) R.drawable.ic_voice_pin_24 else R.drawable.ic_voice_24
}
