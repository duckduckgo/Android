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

package com.duckduckgo.app.browser.tabs.transition

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface NewTabTransitionState {
    val isRunning: StateFlow<Boolean>

    /**
     * The tab the browser is showing, or about to show.
     */
    var shownTabId: String?

    fun setRunning(running: Boolean)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNewTabTransitionState @Inject constructor() : NewTabTransitionState {

    private val running = MutableStateFlow(false)

    override val isRunning: StateFlow<Boolean> = running.asStateFlow()

    override var shownTabId: String? = null

    override fun setRunning(running: Boolean) {
        this.running.value = running
    }
}
