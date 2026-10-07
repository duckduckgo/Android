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

package com.duckduckgo.browser.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the user is entering a query in the browser's address bar, through either the omnibar or
 * the native input field. Bound in `ActivityScope`, so it reflects the browser tab shown in the
 * current activity.
 */
interface OmnibarFocusState {
    /** `true` while the address bar input is focused, `false` otherwise or while the browser is not resumed. */
    val isFocused: StateFlow<Boolean>
}
