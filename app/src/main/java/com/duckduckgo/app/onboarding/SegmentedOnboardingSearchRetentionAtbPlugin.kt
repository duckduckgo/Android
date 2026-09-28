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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SegmentedOnboardingSearchRetentionAtbPlugin @Inject constructor(
    private val onboardingStore: OnboardingStore,
    private val segmentedOnboardingExperimentMetrics: SegmentedOnboardingExperimentMetrics,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        fireForStoredDownloadReason()
    }

    // Duck.ai prompts also count towards search retention
    override fun onDuckAiRetentionAtbRefreshed(oldAtb: String, newAtb: String, metadata: Map<String, String?>) {
        fireForStoredDownloadReason()
    }

    private fun fireForStoredDownloadReason() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val reason = onboardingStore.getDownloadReason() ?: return@launch
            segmentedOnboardingExperimentMetrics.fireDownloadReasonSearchRetentionMetrics(reason)
        }
    }
}
