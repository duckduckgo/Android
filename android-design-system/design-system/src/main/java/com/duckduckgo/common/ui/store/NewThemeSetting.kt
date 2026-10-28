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

package com.duckduckgo.common.ui.store

import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject

/**
 * Reads the `newTheme` toggle once per process, so a remote config change mid-session never mixes
 * old and new themes across activities. The first read wins until the process restarts.
 */
@SingleIn(AppScope::class)
class NewThemeSetting @Inject constructor(
    private val toggles: AppBrandDesignUpdateToggles,
) {
    val useNewTheme: Boolean by lazy { toggles.newTheme().isEnabled() }
}
