/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.newtab

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STOPPED
import com.duckduckgo.app.browser.newtab.LowPriorityMessage.DefaultBrowserMessage
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command
import com.duckduckgo.app.browser.newtab.NewTabLegacyPageViewModel.Command.LaunchDefaultBrowser
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.view.MessageCta.Message
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

@ContributesBinding(AppScope::class)
class LowPriorityMessagingModelImpl @Inject constructor(
    private val defaultBrowserPromptsDataStore: DefaultBrowserPromptsDataStore,
    private val pixel: Pixel,
    private val context: Context,
) : LowPriorityMessagingModel {

    private var lowPriorityMessage: LowPriorityMessage? = null

    override suspend fun getMessage(): LowPriorityMessage? {
        return determineLowPriorityMessage().also {
            lowPriorityMessage = it
        }
    }

    override fun onMessageShown() {
        lowPriorityMessage?.onMessageShown()
    }

    override fun getPrimaryButtonCommand(): Command? {
        return lowPriorityMessage?.getPrimaryAction()
    }

    private suspend fun determineLowPriorityMessage(): LowPriorityMessage? {
        return when {
            defaultBrowserPromptsDataStore.showSetAsDefaultMessage.firstOrNull() == true -> {
                DefaultBrowserMessage(
                    Message(
                        topIllustration = com.duckduckgo.mobile.android.R.drawable.ic_device_mobile_default,
                        title = context.getString(R.string.newTabPageDefaultBrowserMessageHeading),
                        action = context.getString(R.string.newTabPageDefaultBrowserMessagePrimaryCta),
                        action2 = context.getString(R.string.newTabPageDefaultBrowserMessageSecondaryCta),
                    ),
                    onPrimaryAction = {
                        pixel.fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_CLICK)
                        defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(false)
                    },
                    onSecondaryAction = {
                        pixel.fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_DO_NOT_ASK_AGAIN_CLICK)
                        defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(false)
                        defaultBrowserPromptsDataStore.storeExperimentStage(STOPPED)
                    },
                    onClose = {
                        pixel.fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_DISMISSED)
                        defaultBrowserPromptsDataStore.storeShowSetAsDefaultMessageState(false)
                    },
                    onShown = {
                        pixel.fire(AppPixelName.SET_AS_DEFAULT_MESSAGE_IMPRESSION)
                    },
                )
            }

            else -> null
        }
    }
}

sealed class LowPriorityMessage {
    abstract val message: Message
    abstract suspend fun onPrimaryButtonClicked()
    abstract suspend fun onSecondaryButtonClicked()
    abstract suspend fun onCloseButtonClicked()
    abstract fun onMessageShown()
    abstract fun getPrimaryAction(): Command

    data class DefaultBrowserMessage(
        override val message: Message,
        private val onPrimaryAction: suspend () -> Unit,
        private val onSecondaryAction: suspend () -> Unit,
        private val onClose: suspend () -> Unit,
        private val onShown: () -> Unit,
    ) : LowPriorityMessage() {
        override suspend fun onPrimaryButtonClicked() {
            onPrimaryAction()
        }

        override suspend fun onSecondaryButtonClicked() {
            onSecondaryAction()
        }

        override suspend fun onCloseButtonClicked() {
            onClose()
        }

        override fun onMessageShown() {
            onShown()
        }

        override fun getPrimaryAction(): Command {
            return LaunchDefaultBrowser
        }
    }

    // Add here other low priority messages as needed.
    // The message to display is determined by the `determineLowPriorityMessage` function.
}
