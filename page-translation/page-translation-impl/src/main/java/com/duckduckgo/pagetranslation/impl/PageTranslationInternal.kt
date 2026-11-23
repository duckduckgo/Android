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

package com.duckduckgo.pagetranslation.impl

import com.duckduckgo.pagetranslation.api.PageTranslation

interface PageTranslationInternal : PageTranslation {
    /**
     * Decides what to offer for the page described by [input].
     * [hasLanguagePair] tells whether the manifest has a pair from one language to another.
     */
    fun decide(
        input: OfferInput,
        hasLanguagePair: (from: String, to: String) -> Boolean,
    ): TranslationOffer
}
