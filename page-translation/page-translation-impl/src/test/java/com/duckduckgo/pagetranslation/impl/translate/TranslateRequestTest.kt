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

import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class TranslateRequestTest {

    private val pair = LanguagePair("es", "en")
    private val languagePacks: LanguagePacks = mock()
    private val translateAction: TranslateAction = mock()
    private val downloadPrompt: LanguagePackDownloadPrompt = mock()
    private val testee = RealTranslateRequest(languagePacks, translateAction, downloadPrompt)

    @Test
    fun whenPackInstalledThenTranslates() {
        whenever(languagePacks.isInstalled(pair)).thenReturn(true)

        testee.translate(pair)

        verify(translateAction).translate(pair)
        verifyNoInteractions(downloadPrompt)
    }

    @Test
    fun whenPackMissingThenAsksToDownload() {
        whenever(languagePacks.isInstalled(pair)).thenReturn(false)

        testee.translate(pair)

        verify(downloadPrompt).requestDownload(pair)
        verify(translateAction, never()).translate(pair)
    }
}
