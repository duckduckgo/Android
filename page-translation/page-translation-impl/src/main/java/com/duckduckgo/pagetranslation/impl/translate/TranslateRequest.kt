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

package com.duckduckgo.pagetranslation.impl.translate

import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import javax.inject.Inject

/**
 * The user asked to translate the page. The banner and the menu row share it so they cannot decide differently.
 */
interface TranslateRequest {
    /**
     * Translates the page, or asks to download the language pack first when [pair] isn't installed.
     */
    fun translate(pair: LanguagePair)
}

@ContributesBinding(FragmentScope::class, binding = binding<TranslateRequest>())
class RealTranslateRequest @Inject constructor(
    private val languagePacks: LanguagePacks,
    private val translateAction: TranslateAction,
    private val downloadPrompt: LanguagePackDownloadPrompt,
) : TranslateRequest {
    override fun translate(pair: LanguagePair) {
        if (languagePacks.isInstalled(pair)) {
            translateAction.translate(pair)
        } else {
            downloadPrompt.requestDownload(pair)
        }
    }
}
