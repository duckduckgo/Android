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

package com.duckduckgo.app.global.rating

import androidx.annotation.UiThread
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

class AppEnjoymentAppCreationObserver(
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val promptTypeDecider: PromptTypeDecider,
    private val appCoroutineScope: CoroutineScope,
    private val preventDialogQueuingFeature: PreventFeedbackDialogQueuingFeature,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
) : MainProcessLifecycleObserver {

    @UiThread
    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatchers.main()) {
            val shouldPreventQueueing = withContext(dispatchers.io()) {
                preventDialogQueuingFeature.self().isEnabled()
            }

            if (shouldPreventQueueing) {
                // Only determine prompt type if no prompt is currently being shown
                // This prevents queueing up multiple dialogs when the app is backgrounded and foregrounded
                val currentPromptType = appEnjoymentPromptEmitter.promptType.value
                if (currentPromptType == null || currentPromptType == AppEnjoymentPromptOptions.ShowNothing) {
                    appEnjoymentPromptEmitter.promptType.value = promptTypeDecider.determineInitialPromptType()
                } else {
                    logcat { "app enjoyment prompt type already showing, won't show another on top" }
                }
            } else {
                // Original behavior: always determine prompt type on app start
                appEnjoymentPromptEmitter.promptType.value = promptTypeDecider.determineInitialPromptType()
            }
        }
    }
}

/**
 * Feature flag to prevent feedback dialogs from queueing up when the app is backgrounded and foregrounded
 * This is for fixing the bug where dialogs would accumulate if users repeatedly background the app
 *
 * In case of unexpected side-effects, the old behaviour can be reverted by disabling this remote feature flag
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidPreventFeedbackDialogQueueing",
)
interface PreventFeedbackDialogQueuingFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
