/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.newtab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.impl.pixels.RemoteMessagingPixels
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class SharePromoLinkBroadCastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var remoteMessagingRepository: RemoteMessagingRepository

    @Inject
    @AppCoroutineScope
    lateinit var coroutineScope: CoroutineScope

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var pixels: RemoteMessagingPixels

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        AndroidInjection.inject(this, context)
        onPromoLinkSharedSuccessfully()
    }

    private fun onPromoLinkSharedSuccessfully() {
        coroutineScope.launch(dispatcherProvider.io()) {
            remoteMessagingRepository.messageFlow().map { it?.id }.take(1).collect {
                val messageId = it.orEmpty()
                remoteMessagingRepository.dismissMessage(messageId)
                val pixelsParams: Map<String, String> = mapOf(
                    Pixel.PixelParameter.MESSAGE_SHOWN to messageId,
                    Pixel.PixelParameter.ACTION_SUCCESS to "true",
                )

                pixels.fireRemoteMessageSharedPixel(pixelsParams)
            }
        }
    }
}
