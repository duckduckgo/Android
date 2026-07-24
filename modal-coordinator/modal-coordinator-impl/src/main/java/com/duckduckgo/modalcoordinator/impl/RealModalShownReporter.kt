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

package com.duckduckgo.modalcoordinator.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalShownReporter
import com.duckduckgo.modalcoordinator.impl.store.ModalEvaluatorCompletionStore
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealModalShownReporter @Inject constructor(
    private val completionStore: ModalEvaluatorCompletionStore,
    private val promptsCoordinator: PromptsCoordinator,
) : ModalShownReporter {

    override fun reportModalShown() {
        completionStore.recordCompletionSync()
    }

    override fun reportModalDismissed() {
        // The claim was taken by the coordinated evaluation pass that scheduled the modal; the
        // modal-coordinator owns releasing it so call-sites never touch the prompts-coordinator.
        promptsCoordinator.onClaimDone(PromptType.MODAL)
    }
}
