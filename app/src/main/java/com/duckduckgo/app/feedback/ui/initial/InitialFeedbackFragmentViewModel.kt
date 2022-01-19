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

package com.duckduckgo.app.feedback.ui.initial

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.ui.common.ViewState
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

class InitialFeedbackFragmentViewModel : ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: MutableLiveData<Command> = MutableLiveData()

    fun onPositiveFeedback() {
        command.value = Command.PositiveFeedbackSelected
    }

    fun onNegativeFeedback() {
        command.value = Command.NegativeFeedbackSelected
    }

    sealed class Command {
        object PositiveFeedbackSelected : Command()
        object NegativeFeedbackSelected : Command()
        object UserCancelled : Command()
    }
}

@ContributesMultibinding(AppScope::class)
class InitialFeedbackFragmentViewModelFactory @Inject constructor() : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(InitialFeedbackFragmentViewModel::class.java) -> (InitialFeedbackFragmentViewModel() as T)
                else -> null
            }
        }
    }
}
