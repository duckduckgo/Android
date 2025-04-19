/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class OnboardingViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val pageLayoutManager: OnboardingPageManager,
    private val dispatchers: DispatcherProvider,
    private val onboardingSkipper: OnboardingSkipper,
    private val appBuildConfig: AppBuildConfig,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun initializePages() {
        pageLayoutManager.buildPageBlueprints()
    }

    fun pageCount(): Int {
        return pageLayoutManager.pageCount()
    }

    fun getItem(position: Int): OnboardingPageFragment? {
        return pageLayoutManager.buildPage(position)
    }

    fun onOnboardingDone() {
        // Executing this on IO to avoid any delay changing threads between Main-IO.
        viewModelScope.launch(dispatchers.io()) {
            userStageStore.stageCompleted(AppStage.NEW)
        }
    }

    fun onOnboardingSkipped() {
        viewModelScope.launch(dispatchers.io()) {
            onboardingSkipper.markOnboardingAsCompleted()
        }
    }

    fun initializeOnboardingSkipper() {
        if (!appBuildConfig.canSkipOnboarding) return

        // delay showing skip button until privacy config downloaded
        viewModelScope.launch {
            onboardingSkipper.privacyConfigDownloaded.collect {
                _viewState.value = _viewState.value.copy(canShowSkipOnboardingButton = it.skipOnboardingPossible)
            }
        }
    }

    suspend fun devOnlyFullyCompleteAllOnboarding() {
        onboardingSkipper.markOnboardingAsCompleted()
    }

    companion object {
        data class ViewState(val canShowSkipOnboardingButton: Boolean = false)
    }
}
