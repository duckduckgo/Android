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

package com.duckduckgo.pagetranslation.impl.menu

/**
 * What the translation row of the browser menu shows. It is also what the dot on the menu button follows.
 */
sealed interface TranslationMenuViewState {
    /**
     * No row: the page can't be translated or no translation is offered.
     */
    data object Hidden : TranslationMenuViewState

    /**
     * The row offers to translate the page.
     *
     * @param target code of the language the page would be translated to.
     * @param enabled false while the language pack is downloading and translating can't start.
     */
    data class Translate(val target: String, val enabled: Boolean) : TranslationMenuViewState

    /**
     * The row shows that the page is being translated, and can't be tapped.
     */
    data object InProgress : TranslationMenuViewState

    /**
     * The page is translated and the row switches back to the original.
     */
    data object ShowOriginal : TranslationMenuViewState
}
