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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.store.TranslationPreferencesStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface TranslationOfferRules {
    /**
     * Decides what to offer for the page described by the parameters.
     *
     * @param content What the tab currently shows.
     * @param detection Null while detection isn't available yet, e.g. the main frame hasn't finished loading.
     * @param bannerSuppressed True on the reload after Show Original or after a failed translation.
     * @param availablePairs The manifest's language pairs, each as "<from>-<to>", e.g. "es-en".
     */
    suspend fun decide(
        content: TabContent,
        detection: Detection?,
        bannerSuppressed: Boolean,
        availablePairs: Set<String>,
    ): TranslationOffer
}

@ContributesBinding(AppScope::class, binding = binding<TranslationOfferRules>())
class RealTranslationOfferRules @Inject constructor(
    private val deviceLanguages: DeviceLanguages,
    private val preferencesStore: TranslationPreferencesStore,
) : TranslationOfferRules {

    override suspend fun decide(
        content: TabContent,
        detection: Detection?,
        bannerSuppressed: Boolean,
        availablePairs: Set<String>,
    ): TranslationOffer {
        if (content != TabContent.WebPage) return TranslationOffer.None

        val reliableDetection = detection?.takeIf { it.isReliable } ?: return TranslationOffer.None
        val detected = reliableDetection.language
        val languages = deviceLanguages.get().map { it.baseLanguage() }
        val preferred = languages.firstOrNull() ?: return TranslationOffer.None

        if (detected == preferred) return TranslationOffer.None
        if (pairKey(detected, preferred) !in availablePairs) return TranslationOffer.None

        val bannerAllowed = detected !in languages && !bannerSuppressed && (preferencesStore.askToTranslatePages.firstOrNull() ?: true)
        return if (bannerAllowed) TranslationOffer.BannerAndMenuRow else TranslationOffer.MenuRowOnly
    }

    private fun pairKey(from: String, to: String) = "$from-$to"

    private fun String.baseLanguage(): String = substringBefore('-').substringBefore('_').lowercase()
}
