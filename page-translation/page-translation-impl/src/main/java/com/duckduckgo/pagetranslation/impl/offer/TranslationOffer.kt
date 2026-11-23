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

import com.duckduckgo.pagetranslation.api.TabContent

enum class TranslationOffer {
    BannerAndMenuRow,
    MenuRowOnly,
    Nothing,
}

data class Detection(val language: String)

data class OfferInput(
    /**
     * What the tab currently shows.
     */
    val content: TabContent,
    /**
     * Null until the main frame finishes loading, and when the detection isn't reliable.
     */
    val detection: Detection?,
    /**
     * True on the reload after Show Original or after a failed translation.
     */
    val bannerSuppressed: Boolean,
)
