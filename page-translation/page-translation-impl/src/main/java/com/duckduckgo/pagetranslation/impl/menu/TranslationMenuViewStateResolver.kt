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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.api.TabContent
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.offer.TranslationOffer
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/**
 * What a tab's translation session offers the menu.
 *
 * @param offer what the session offers for the page.
 * @param pair the languages to translate between, also when only the menu row is offered. Null when there is none.
 * @param session what the session is doing.
 */
data class TabTranslationSnapshot(
    val offer: TranslationOffer,
    val pair: LanguagePair?,
    val session: SessionState,
)

interface TranslationMenuViewStateResolver {
    /**
     * Decides the translation row of the menu for one tab.
     *
     * @param content what the tab shows. Anything but a web page is [TranslationMenuViewState.Hidden].
     * @param snapshot the tab's translation session. Offering [TranslationOffer.None], or having no pair, hides the translate row;
     * a running or finished translation is shown whatever was offered.
     */
    fun resolve(content: TabContent, snapshot: TabTranslationSnapshot): TranslationMenuViewState
}

@ContributesBinding(AppScope::class)
class RealTranslationMenuViewStateResolver @Inject constructor() : TranslationMenuViewStateResolver {
    override fun resolve(content: TabContent, snapshot: TabTranslationSnapshot): TranslationMenuViewState {
        if (content != TabContent.WebPage) return TranslationMenuViewState.Hidden
        return when (snapshot.session) {
            SessionState.Translated -> TranslationMenuViewState.ShowOriginal
            SessionState.Translating -> TranslationMenuViewState.InProgress
            SessionState.Downloading -> translate(snapshot, enabled = false)
            SessionState.Idle -> translate(snapshot, enabled = true)
        }
    }

    private fun translate(snapshot: TabTranslationSnapshot, enabled: Boolean): TranslationMenuViewState {
        val pair = snapshot.pair
        return if (snapshot.offer == TranslationOffer.None || pair == null) {
            TranslationMenuViewState.Hidden
        } else {
            TranslationMenuViewState.Translate(pair.target, enabled)
        }
    }
}
