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

package com.duckduckgo.pir.impl.onboarding

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingController
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepOutcome.COMPLETED
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SubscriptionOnboardingPirStepViewModel @Inject constructor(
    private val pirRepository: PirRepository,
    private val controller: SubscriptionOnboardingController,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    /**
     * Called when the user comes back from the PIR flow: reports the step completed if they started a scan
     * (they stored at least one profile query). A no-op otherwise, so the step stays available.
     */
    suspend fun completeIfScanStarted() {
        val scanStarted = withContext(dispatcherProvider.io()) {
            pirRepository.getAllUserProfileQueries().isNotEmpty()
        }
        if (scanStarted) {
            controller.onStepFinished(PIR_STEP_ID, COMPLETED)
        }
    }

    companion object {
        const val PIR_STEP_ID = "pir"
    }
}
