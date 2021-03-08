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

package com.duckduckgo.app.feedback.ui.negative.brokensite

import androidx.lifecycle.ViewModel
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class BrokenSiteNegativeFeedbackViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideBrokenSiteNegativeFeedbackViewModelFactory(): ViewModelFactoryPlugin {
        return BrokenSiteNegativeFeedbackViewModelFactory()
    }
}

private class BrokenSiteNegativeFeedbackViewModelFactory() : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BrokenSiteNegativeFeedbackViewModel::class.java) -> (BrokenSiteNegativeFeedbackViewModel() as T)
                else -> null
            }
        }
    }
}

class BrokenSiteNegativeFeedbackViewModel : ViewModel() {

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    fun userSubmittingFeedback(feedback: String, brokenSite: String?) {
        command.value = Command.ExitAndSubmitFeedback(feedback, brokenSite)
    }

    sealed class Command {
        data class ExitAndSubmitFeedback(val feedback: String, val brokenSite: String?) : Command()
        object Exit : Command()
    }
}
