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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** What the engine gives a binder at bind time. */
class BindScope(
    /** Cancelled by the engine at unbind, so state observation dies with the binding. */
    val coroutineScope: CoroutineScope,
    val execute: (ContentInteraction) -> Unit,
    /**
     * Asks the card to tween its bounds, over the given duration, into the layout change the binder makes next:
     * a view it reveals or resizes would otherwise resize the card in a single frame. Call it immediately before
     * that change. No-op while a snapped or skipped entrance is still landing the screen in place.
     */
    val animateCardBounds: (durationMs: Long) -> Unit,
)

/** Interactions a bound screen raises outside the shared CTA buttons interactions. */
sealed interface ContentInteraction {

    data class SubmitInputPreview(
        val query: String,
        val isChat: Boolean,
        val fromSuggestion: Boolean,
    ) : ContentInteraction

    data object QuickSetupEditAddressBarPosition : ContentInteraction

    data object QuickSetupEditSearchOptions : ContentInteraction

    data class QuickSetupSetDefaultBrowser(val checked: Boolean) : ContentInteraction

    data class QuickSetupAddWidget(val checked: Boolean) : ContentInteraction
}

/** Binds a stateless [ContentConfig] to its include layout. */
interface DialogBinder<C : ContentConfig> {

    /** The include root, shown and hidden by the engine. */
    val view: View

    fun bind(content: C, scope: BindScope): ContentHandle
}

/** Binds a [Stateful] [ContentConfig], observing and mutating the store-owned [MutableStateFlow]. */
interface StatefulDialogBinder<C, S : Any> where C : ContentConfig, C : Stateful<S> {

    /** The include root, shown and hidden by the engine. */
    val view: View

    fun bind(content: C, state: MutableStateFlow<S>, scope: BindScope): ContentHandle
}
