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

import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.offer.TranslationOffer
import kotlinx.coroutines.flow.StateFlow

/**
 * What a tab's menu row needs from its translation session.
 */
interface TranslationMenuSource {
    val menuOffer: StateFlow<TranslationOffer>

    /**
     * The pair to translate, also when only the menu row is offered.
     */
    val pair: StateFlow<LanguagePair?>

    val state: StateFlow<SessionState>

    fun showOriginal()
}
