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

import com.duckduckgo.pagetranslation.impl.offer.TranslationOfferSource
import com.duckduckgo.pagetranslation.impl.translate.TranslateRequest
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
    private val translateRequest: TranslateRequest,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private val _state = MutableStateFlow<BannerState>(BannerState.Hidden(HiddenReason.NoOffer()))
    val state: StateFlow<BannerState> = _state

    private val onScreen = MutableStateFlow(false)

    private val interacting = MutableStateFlow(false)

    fun setOnScreen(value: Boolean) {
        onScreen.value = value
    }

    /**
     * While the user touches or drags the banner the timer is paused, and it restarts in full on release.
     */
    fun setInteracting(value: Boolean) {
        interacting.value = value
    }

    suspend fun run() = coroutineScope {
        launch {
            offerSource.offer.collect { offer -> reduce(BannerEvent.OfferChanged(offer)) }
        }
        launch {
            combine(state.map { (it as? BannerState.Showing)?.offer }, onScreen, interacting) { offer, visible, touched ->
                offer.takeIf { visible && !touched }
            }
                .distinctUntilChanged()
                .collectLatest { offer ->
                    if (offer != null) {
                        delay(timeoutMillis.milliseconds)
                        reduce(BannerEvent.Dismiss(Dismissal.Timeout))
                    }
                }
        }
    }

    suspend fun onTranslate() {
        val offer = (state.value as? BannerState.Showing)?.offer ?: return
        reduce(BannerEvent.Dismiss(Dismissal.Translate))
        translateRequest.translate(offer.pair)
    }

    fun onDismiss(by: Dismissal) {
        reduce(BannerEvent.Dismiss(by))
    }

    private fun reduce(event: BannerEvent) {
        _state.update { BannerStateMachine.reduce(it, event) }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
    }
}
