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

package com.duckduckgo.pagetranslation.impl.session

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.pagetranslation.impl.engine.TranslationEngine
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.messaging.PageTranslationMessaging
import com.duckduckgo.pagetranslation.impl.translate.TranslateAction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import javax.inject.Inject

sealed interface TranslationState {
    /** Nothing has been translated in the current document, or there is no document. */
    data object Idle : TranslationState

    data object Translating : TranslationState

    /** The viewport is translated. The rest of the page may still be translating. */
    data object Translated : TranslationState

    /** The viewport couldn't be translated. [hasAppliedUnits] when part of it was. */
    data class Failed(val hasAppliedUnits: Boolean) : TranslationState
}

/** The translation of the document a tab shows. Call every method on the main thread. */
interface TabTranslation {
    /** The current document's translation, [TranslationState.Idle] without one. */
    val state: StateFlow<TranslationState>

    /** Starts a session for the document whose `performance.timeOrigin` is [timeOrigin], ending the previous one. */
    fun startSession(timeOrigin: Double)

    /** Ends the current session, if any, and drops its results. */
    fun endSession()

    /** While the tab is hidden, translation waits between batches. */
    fun setVisible(isVisible: Boolean)
}

@SingleIn(FragmentScope::class)
@ContributesBinding(FragmentScope::class, binding = binding<TabTranslation>())
@ContributesBinding(FragmentScope::class, binding = binding<TranslateAction>())
class RealTabTranslation @Inject constructor(
    private val messaging: PageTranslationMessaging,
    private val engine: TranslationEngine,
    @AppCoroutineScope appScope: CoroutineScope,
    dispatchers: DispatcherProvider,
) : TabTranslation, TranslateAction {
    private val sessionScope = appScope + dispatchers.main()
    private val isVisible = MutableStateFlow(false)
    private val _state = MutableStateFlow<TranslationState>(TranslationState.Idle)
    override val state: StateFlow<TranslationState> = _state

    private var session: TranslationSession? = null

    override fun startSession(timeOrigin: Double) {
        endSession()
        session = TranslationSession(timeOrigin, messaging, engine, isVisible, _state, sessionScope)
    }

    override fun endSession() {
        session?.end()
        session = null
        _state.value = TranslationState.Idle
    }

    override fun setVisible(isVisible: Boolean) {
        this.isVisible.value = isVisible
    }

    override fun translate(pair: LanguagePair) {
        session?.translate(pair)
    }
}
