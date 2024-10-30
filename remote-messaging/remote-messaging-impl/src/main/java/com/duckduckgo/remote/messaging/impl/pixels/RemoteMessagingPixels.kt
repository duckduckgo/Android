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
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixelName.REMOTE_MESSAGE_SHOWN_UNIQUE
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface RemoteMessagingPixels {

    fun fireRemoteMessageShownPixel(remoteMessage: RemoteMessage)
    fun fireRemoteMessageDismissedPixel(remoteMessage: RemoteMessage)
    fun fireRemoteMessagePrimaryActionClickedPixel(remoteMessage: RemoteMessage)
    fun fireRemoteMessageSecondaryActionClickedPixel(remoteMessage: RemoteMessage)
    fun fireRemoteMessageActionClickedPixel(remoteMessage: RemoteMessage)
    fun fireRemoteMessageSharedPixel(remoteMessage: Map<String, String>)
}

@ContributesBinding(AppScope::class)
class RealRemoteMessagingPixels @Inject constructor(
    private val pixel: Pixel,
) : RemoteMessagingPixels {
    override fun fireRemoteMessageShownPixel(remoteMessage: RemoteMessage) {
        pixel.fire(
            pixel = REMOTE_MESSAGE_SHOWN_UNIQUE,
            parameters = remoteMessage.asPixelParams(),
            type = Unique("${REMOTE_MESSAGE_SHOWN_UNIQUE.pixelName}_${remoteMessage.id}"),
        )
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_SHOWN, parameters = remoteMessage.asPixelParams())
    }

    override fun fireRemoteMessageDismissedPixel(remoteMessage: RemoteMessage) {
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_DISMISSED, parameters = remoteMessage.asPixelParams())
    }

    override fun fireRemoteMessagePrimaryActionClickedPixel(remoteMessage: RemoteMessage) {
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_PRIMARY_ACTION_CLICKED, parameters = remoteMessage.asPixelParams())
    }

    override fun fireRemoteMessageSecondaryActionClickedPixel(remoteMessage: RemoteMessage) {
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_SECONDARY_ACTION_CLICKED, parameters = remoteMessage.asPixelParams())
    }

    override fun fireRemoteMessageActionClickedPixel(remoteMessage: RemoteMessage) {
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_ACTION_CLICKED, parameters = remoteMessage.asPixelParams())
    }

    override fun fireRemoteMessageSharedPixel(pixelsParams: Map<String, String>) {
        pixel.fire(pixel = RemoteMessagingPixelName.REMOTE_MESSAGE_SHARED, parameters = pixelsParams)
    }
}

private fun RemoteMessage.asPixelParams(): Map<String, String> = mapOf(Pixel.PixelParameter.MESSAGE_SHOWN to this.id)
