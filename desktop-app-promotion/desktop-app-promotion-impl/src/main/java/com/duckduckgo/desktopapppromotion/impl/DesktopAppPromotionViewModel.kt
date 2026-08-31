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

package com.duckduckgo.desktopapppromotion.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.desktopapppromotion.api.DesktopAppPromotionInteractionHandler.Interaction
import com.duckduckgo.desktopapppromotion.api.PixelConfig
import com.duckduckgo.desktopapppromotion.api.PixelFireSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DesktopAppPromotionViewModel @AssistedInject constructor(
    @Assisted private val content: DesktopAppPromotionContent,
    @Assisted private val pixels: PixelConfig,
    @Assisted private val handlerId: String?,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val clipboardInteractor: ClipboardInteractor,
    private val interactionDispatcher: DesktopAppPromotionInteractionDispatcher,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState(content))
    private val _commands = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val viewState: Flow<ViewState> = _viewState.asStateFlow()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        viewModelScope.launch(dispatchers.io()) {
            fire(pixels.impression)
        }
    }

    fun onShareClicked() {
        viewModelScope.launch(dispatchers.io()) {
            fire(pixels.shareClicked ?: PixelFireSpec(DesktopAppPromotionPixels.SHARE_DOWNLOAD_LINK_CLICK))
            _commands.send(
                Command.ShareLink(
                    shareText = content.shareIntentBody ?: content.downloadUrl,
                    chooserTitle = content.shareIntentTitle,
                ),
            )
        }
    }

    fun onLinkClicked() {
        viewModelScope.launch(dispatchers.io()) {
            if (!clipboardInteractor.copyToClipboard(content.downloadUrl, isSensitive = false)) {
                _commands.send(Command.ShowCopiedNotification)
            }
            fire(pixels.linkClicked ?: PixelFireSpec(DesktopAppPromotionPixels.LINK_CLICK))
            interactionDispatcher.dispatch(handlerId, Interaction.LINK_COPIED)
        }
    }

    fun onDismissClicked() {
        viewModelScope.launch(dispatchers.io()) {
            fire(pixels.dismissed)
            interactionDispatcher.dispatch(handlerId, Interaction.DISMISSED)
            _commands.send(Command.Close)
        }
    }

    fun onBackPressed() {
        viewModelScope.launch {
            _commands.send(Command.Close)
        }
    }

    private fun fire(spec: PixelFireSpec?) {
        spec?.let { pixel.fire(it.pixelName, it.parameters) }
    }

    data class ViewState(val content: DesktopAppPromotionContent)

    sealed class Command {
        data class ShareLink(
            val shareText: String,
            val chooserTitle: String,
        ) : Command()

        data object ShowCopiedNotification : Command()
        data object Close : Command()
    }

    @AssistedFactory
    interface Factory {
        fun create(
            content: DesktopAppPromotionContent,
            pixels: PixelConfig,
            handlerId: String?,
        ): DesktopAppPromotionViewModel
    }
}
