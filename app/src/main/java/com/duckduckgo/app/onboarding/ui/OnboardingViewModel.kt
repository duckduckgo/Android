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
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.launch
import javax.inject.Singleton

class OnboardingViewModel(
    private val userStageStore: UserStageStore,
    private val pageLayoutManager: OnboardingPageManager,
    private val dispatchers: DispatcherProvider
) : ViewModel() {

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
}

@Module
@ContributesTo(AppObjectGraph::class)
class OnboardingViewModelFactoryModule {
    @Provides
    @Singleton
    @IntoSet
    fun provideOnboardingViewModelFactory(
        userStageStore: UserStageStore,
        pageLayoutManager: OnboardingPageManager,
        dispatchers: DispatcherProvider
    ): ViewModelFactoryPlugin {
        return OnboardingViewModelFactory(userStageStore, pageLayoutManager, dispatchers)
    }
}

private class OnboardingViewModelFactory(
    private val userStageStore: UserStageStore,
    private val pageLayoutManager: OnboardingPageManager,
    private val dispatchers: DispatcherProvider
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(OnboardingViewModel::class.java) -> (OnboardingViewModel(userStageStore, pageLayoutManager, dispatchers) as T)
                else -> null
            }
        }
    }
}
