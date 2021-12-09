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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppEnjoymentAppCreationObserver(
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val promptTypeDecider: PromptTypeDecider,
    private val appCoroutineScope: CoroutineScope
) : LifecycleObserver {

    @UiThread
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppStart() {
        appCoroutineScope.launch(Dispatchers.Main) {
            appEnjoymentPromptEmitter.promptType.value = promptTypeDecider.determineInitialPromptType()
        }
    }
}
