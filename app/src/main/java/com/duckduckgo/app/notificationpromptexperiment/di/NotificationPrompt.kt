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

package com.duckduckgo.app.notificationpromptexperiment.di

import com.duckduckgo.app.notificationpromptexperiment.NotificationPromptExperimentManager
import com.duckduckgo.app.notificationpromptexperiment.NotifyMeListenerImpl
import com.duckduckgo.common.ui.notifyme.NotifyMeListener
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides

@Module
@ContributesTo(AppScope::class)
class NotificationPrompt {

    @Provides
    fun providesNotifyMeListener(
        notificationPromptExperimentManager: NotificationPromptExperimentManager,
    ): NotifyMeListener {
        return NotifyMeListenerImpl(notificationPromptExperimentManager)
    }
}
