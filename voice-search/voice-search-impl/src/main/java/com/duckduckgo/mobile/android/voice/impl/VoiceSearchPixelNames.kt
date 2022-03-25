/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.voice.impl

import com.duckduckgo.app.statistics.pixels.Pixel

enum class VoiceSearchPixelNames(override val pixelName: String) : Pixel.PixelName {
    VOICE_SEARCH_AVAILABLE("m_voice_search_available"),
    VOICE_SEARCH_PRIVACY_DIALOG_ACCEPTED("m_voice_search_privacy_dialog_accepted"),
    VOICE_SEARCH_PRIVACY_DIALOG_REJECTED("m_voice_search_privacy_dialog_rejected"),
    VOICE_SEARCH_STARTED("m_voice_search_started"),
    VOICE_SEARCH_DONE("m_voice_search_done")
}
