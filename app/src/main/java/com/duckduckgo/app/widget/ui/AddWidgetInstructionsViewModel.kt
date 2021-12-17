/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.widget.ui

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.Close
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.ShowHome
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

class AddWidgetInstructionsViewModel : ViewModel() {

    sealed class Command {
        object ShowHome : Command()
        object Close : Command()
    }

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun onShowHomePressed() {
        command.value = ShowHome
    }

    fun onClosePressed() {
        command.value = Close
    }
}

@ContributesMultibinding(AppScope::class)
class AddWidgetInstructionsViewModelFactory @Inject constructor() : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(AddWidgetInstructionsViewModel::class.java) ->
                    (AddWidgetInstructionsViewModel() as T)
                else -> null
            }
        }
    }
}
