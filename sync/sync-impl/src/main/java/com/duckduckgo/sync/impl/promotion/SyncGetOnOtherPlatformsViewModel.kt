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

package com.duckduckgo.sync.impl.promotion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShareLink
import com.duckduckgo.sync.impl.promotion.SyncGetOnOtherPlatformsViewModel.Command.ShowCopiedNotification
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class SyncGetOnOtherPlatformsViewModel @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val clipboardInteractor: ClipboardInteractor,
) : ViewModel() {

    private val commandChannel = Channel<Command>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands = commandChannel.receiveAsFlow()

    sealed class Command {
        data class ShareLink(val link: String) : Command()
        data object ShowCopiedNotification : Command()
    }

    data class ViewState(val windowsFeatureEnabled: Boolean)

    fun onShareClicked() {
        viewModelScope.launch {
            commandChannel.send(ShareLink(buildLink()))

            // todo - pixels
        }
    }

    fun onLinkClicked() {
        viewModelScope.launch(dispatchers.io()) {
            if (!clipboardInteractor.copyToClipboard(buildLink(), isSensitive = false)) {
                commandChannel.send(ShowCopiedNotification)
            }

            // todo - pixels
        }
    }

    private fun buildLink(): String {
        return "$BASE_LINK?$ATTRIBUTION"
    }

    companion object {
        private const val BASE_LINK = "https://duckduckgo.com/app"
        private const val ATTRIBUTION = "origin=funnel_browser_android_sync"
    }
}
