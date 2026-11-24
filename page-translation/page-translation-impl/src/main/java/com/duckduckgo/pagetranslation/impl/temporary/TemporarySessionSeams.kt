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

package com.duckduckgo.pagetranslation.impl.temporary

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Temporary bindings until the translation session lands. The session change deletes this class.
 * Nothing is offered and translating does nothing.
 */
@ContributesBinding(FragmentScope::class, binding = binding<TranslationOfferSource>())
@ContributesBinding(FragmentScope::class, binding = binding<TranslateAction>())
@SingleIn(FragmentScope::class)
class TemporarySessionSeams @Inject constructor() : TranslationOfferSource, TranslateAction {

    override val offer: StateFlow<ActiveOffer?> = MutableStateFlow(null)

    override fun translate(pair: LanguagePair) = Unit
}

/**
 * Temporary binding until the language pack downloads land. The downloads change deletes this class.
 * Nothing is installed.
 */
@ContributesBinding(AppScope::class)
class TemporaryLanguagePacks @Inject constructor() : LanguagePacks {
    override fun isInstalled(pair: LanguagePair): Boolean = false
}

/**
 * Temporary binding until the language pack sheet lands. The sheet change deletes this class.
 */
@ContributesBinding(FragmentScope::class)
class TemporaryLanguagePackDownloadPrompt @Inject constructor() : LanguagePackDownloadPrompt {
    override fun requestDownload(pair: LanguagePair) = Unit
}
