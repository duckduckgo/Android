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

package com.duckduckgo.pagetranslation.impl.offer

import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import kotlinx.coroutines.flow.StateFlow

/**
 * A banner offer for one page load.
 *
 * @param document changes with every page load, so a dismissal only applies to the page it was made on.
 */
data class ActiveOffer(
    val pair: LanguagePair,
    val document: Long,
)

interface TranslationOfferSource {
    /**
     * Non-null only while the banner may be shown: the offer is [TranslationOffer.BannerAndMenuRow] and nothing suppresses it,
     * for example Show Original or a failed translation.
     */
    val offer: StateFlow<ActiveOffer?>
}
