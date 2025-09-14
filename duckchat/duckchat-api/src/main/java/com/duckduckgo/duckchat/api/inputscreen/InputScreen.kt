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

package com.duckduckgo.duckchat.api.inputscreen

import android.content.Context

/**
 * InputScreen interface provides methods for managing Input Screen functionality.
 */
interface InputScreen {
    /**
     * Displays the new address bar option choice screen.
     */
    fun showNewAddressBarOptionChoiceScreen(context: Context, isDarkThemeEnabled: Boolean)
}
