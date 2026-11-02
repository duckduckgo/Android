/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.viewstate

data class LoadingViewState(
    val isLoading: Boolean = false,
    val trackersAnimationEnabled: Boolean = true,
    val progress: Int = 0,
    val url: String = "",
) {
    /** Null when a bar is already running, since restarting it would show a second cycle. */
    fun onIntentStart(): LoadingViewState? = if (isLoading) null else copy(isLoading = true, progress = 0)

    /** Progress stays 0 while pending, so a non-zero value means the bar belongs to a page that is still loading. */
    fun onNavigationAbandoned(): LoadingViewState? = if (progress == 0) copy(isLoading = false) else null
}
