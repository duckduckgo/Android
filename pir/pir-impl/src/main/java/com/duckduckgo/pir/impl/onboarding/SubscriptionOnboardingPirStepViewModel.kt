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
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class SubscriptionOnboardingPirStepViewModel @Inject constructor(
    private val pirRepository: PirRepository,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    /** A scan is considered started once the user has stored at least one profile query. */
    suspend fun hasStartedScan(): Boolean = withContext(dispatcherProvider.io()) {
        pirRepository.getAllUserProfileQueries().isNotEmpty()
    }
}
