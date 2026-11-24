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

package com.duckduckgo.pagetranslation.impl.banner

import android.content.Context
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.pagetranslation.impl.language.DeviceLanguages
import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import javax.inject.Inject

class TranslationBannerFactory @Inject constructor(
    private val offerSource: TranslationOfferSource,
    private val languagePacks: LanguagePacks,
    private val translateAction: TranslateAction,
    private val downloadPrompt: LanguagePackDownloadPrompt,
    private val deviceLanguages: DeviceLanguages,
    private val dispatchers: DispatcherProvider,
) {
    fun create(context: Context): TranslationBannerView {
        val controller = BannerController(
            offerSource = offerSource,
            languagePacks = languagePacks,
            translateAction = translateAction,
            downloadPrompt = downloadPrompt,
        )
        return TranslationBannerView(context).also { it.bind(controller, deviceLanguages, dispatchers.main()) }
    }
}
