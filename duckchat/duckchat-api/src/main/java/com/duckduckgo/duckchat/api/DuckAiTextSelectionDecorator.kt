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

package com.duckduckgo.duckchat.api

import android.view.ActionMode

/**
 * Customizes the text selection menu.
 */
interface DuckAiTextSelectionDecorator {

    /**
     * Returns [callback] with "Ask Duck.ai" shown as a primary action rather than an overflow entry.
     * The action is hidden for Duck.ai tabs.
     *
     * @param pageUrl the page the text was selected on.
     */
    fun decorate(callback: ActionMode.Callback?, pageUrl: String?): ActionMode.Callback?
}
