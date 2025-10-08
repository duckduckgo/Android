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

package com.duckduckgo.duckchat.impl.subscription

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_PAID_SETTINGS_OPENED
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class DuckAiPaidSettingsViewModel @Inject constructor(
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    sealed class Command {
        data object OpenDuckAi : Command()
        data class LaunchLearnMoreWebPage(
            val url: String = "https://duckduckgo.com/duckduckgo-help-pages/privacy-pro/",
            @StringRes val titleId: Int = R.string.duck_ai_paid_settings_learn_more_title,
        ) : Command()
    }

    private val _commands = Channel<Command>(1, BufferOverflow.DROP_OLDEST)
    val commands = _commands.receiveAsFlow()

    init {
        pixel.fire(DUCK_CHAT_PAID_SETTINGS_OPENED)
    }

    fun onLearnMoreSelected() {
        viewModelScope.launch {
            _commands.send(Command.LaunchLearnMoreWebPage())
        }
    }

    fun onOpenDuckAiSelected() {
        viewModelScope.launch {
            _commands.send(Command.OpenDuckAi)
            pixel.fire(DUCK_CHAT_PAID_OPEN_DUCK_AI_CLICKED)
        }
    }
}
