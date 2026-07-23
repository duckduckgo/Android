/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the engine gives a binder at bind time.
 *
 * This extends the spec: the spec's handle only covers CTA-built events ([ContentHandle.result]), but
 * input-screen-preview submits from text input and quick setup opens bottom sheets — both content-initiated,
 * not CTA-initiated. [emit] lets bound content forward an orchestrator event directly; [execute] lets it ask
 * the VM to perform a content interaction. Documented POC finding.
 */
class BindScope(
    /** Cancelled by the engine at unbind. */
    val coroutineScope: CoroutineScope,
    /** Content-initiated orchestrator events. */
    val emit: (NewUserOnboardingEvent) -> Unit,
    /** Content-initiated VM interactions. */
    val execute: (ContentInteraction) -> Unit,
)

/** VM interactions a bound screen can trigger on its own, outside of the shared CTA click flow. */
sealed interface ContentInteraction {
    data object EditAddressBarPosition : ContentInteraction
    data object EditSearchOptions : ContentInteraction
    data class SetDefaultBrowserToggled(val checked: Boolean) : ContentInteraction
    data class AddWidgetToggled(val checked: Boolean) : ContentInteraction
}

/** Binds a stateless [ContentConfig] to its include layout. Never touches primary/secondary CTA views, never calls the VM. */
interface DialogBinder<C : ContentConfig> {
    /** Include root; the engine toggles its visibility. */
    val view: View

    fun bind(content: C, scope: BindScope): ContentHandle
}

/** Binds a [Stateful] [ContentConfig] to its include layout, observing/mutating the VM-owned [MutableStateFlow]. */
interface StatefulDialogBinder<C, S : Any> where C : ContentConfig, C : Stateful<S> {
    val view: View

    fun bind(content: C, state: MutableStateFlow<S>, scope: BindScope): ContentHandle
}
