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

package com.duckduckgo.duckchat.impl.nativeinput.footer.attachmentprivacy

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AttachmentPrivacyPixelName(private val baseName: String) {
    IMAGE_SHOWN("m_aichat_attachment_privacy_image_shown"),
    FILE_SHOWN("m_aichat_attachment_privacy_file_shown"),
    IMAGE_LEARN_MORE_TAPPED("m_aichat_attachment_privacy_image_learn_more_tapped"),
    FILE_LEARN_MORE_TAPPED("m_aichat_attachment_privacy_file_learn_more_tapped"),
    ;

    val count: Pixel.PixelName = WireName("${baseName}_count")
    val daily: Pixel.PixelName = WireName("${baseName}_daily")

    private data class WireName(override val pixelName: String) : Pixel.PixelName
}

class AttachmentPrivacyPixels @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {
    fun fire(
        name: AttachmentPrivacyPixelName,
        surface: DuckChatPixelSurface,
    ) {
        val parameters = mapOf(DuckChatPixelParameters.SURFACE to surface.value)
        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(name.count, parameters = parameters)
            pixel.fire(name.daily, parameters = parameters, type = Pixel.PixelType.Daily())
        }
    }
}
