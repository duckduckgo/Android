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

import com.duckduckgo.pagetranslation.impl.language.LanguagePacks
import com.duckduckgo.pagetranslation.impl.offer.ActiveOffer
import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.LanguagePackDownloadPrompt
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Drives one banner view. The timer only runs while the banner is on screen, so it waits while the host hides the container.
 */
class BannerController(
    private val offerSource: TranslationOfferSource,
    private val languagePacks: LanguagePacks,
    private val translateAction: TranslateAction,
    private val downloadPrompt: LanguagePackDownloadPrompt,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val _state = MutableStateFlow<BannerState>(BannerState.Hidden(HiddenReason.NoOffer()))
    val state: StateFlow<BannerState> = _state

    private val onScreen = MutableStateFlow(false)

    fun setOnScreen(value: Boolean) {
        onScreen.value = value
    }

    suspend fun run() = coroutineScope {
        launch {
            offerSource.offer.collect { offer -> reduce(BannerEvent.OfferChanged(offer)) }
        }
        launch {
            combine(state.map { (it as? BannerState.Showing)?.offer }, onScreen) { offer, visible -> offer.takeIf { visible } }
                .distinctUntilChanged()
                .collectLatest { offer ->
                    if (offer != null) {
                        delay(timeoutMillis.milliseconds)
                        reduce(BannerEvent.Dismiss(Dismissal.Timeout))
                    }
                }
        }
    }

    fun onTranslate() {
        val offer = (state.value as? BannerState.Showing)?.offer ?: return
        reduce(BannerEvent.Dismiss(Dismissal.Translate))
        route(offer)
    }

    fun onDismiss(by: Dismissal) {
        reduce(BannerEvent.Dismiss(by))
    }

    private fun route(offer: ActiveOffer) {
        if (languagePacks.isInstalled(offer.pair)) {
            translateAction.translate(offer.pair)
        } else {
            downloadPrompt.requestDownload(offer.pair)
        }
    }

    private fun reduce(event: BannerEvent) {
        _state.update { BannerStateMachine.reduce(it, event) }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
