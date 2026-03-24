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

package com.duckduckgo.app.dev.settings.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class OnboardingDevSettingsViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val settingsDataStore: SettingsDataStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val ctaViewModel: CtaViewModel,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    data class ViewState(
        val onboardingCompleted: Boolean = false,
        val onboardingSkipped: Boolean = false,
        val ctaDismissedStates: Map<CtaId, Boolean> = emptyMap(),
        val visibleCtaIds: List<CtaId> = emptyList(),
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private suspend fun visibleCtaIds(): List<CtaId> {
        val requiredDialogs = ctaViewModel.requiredDaxOnboardingCtas()
        val extraDialogs = listOf(CtaId.DAX_INTRO_VISIT_SITE, CtaId.ADD_WIDGET).filterNot { it in requiredDialogs }
        return requiredDialogs + extraDialogs
    }

    fun start() {
        viewModelScope.launch {
            loadState()
        }
    }

    private suspend fun loadState() {
        withContext(dispatchers.io()) {
            val stage = userStageStore.getUserAppStage()
            val hideTips = settingsDataStore.hideTips
            val completed = stage == AppStage.ESTABLISHED
            val skipped = completed && hideTips
            val visibleCtas = visibleCtaIds()
            val ctaStates = visibleCtas.associateWith { dismissedCtaDao.exists(it) }
            _viewState.value = ViewState(
                onboardingCompleted = completed,
                onboardingSkipped = skipped,
                ctaDismissedStates = ctaStates,
                visibleCtaIds = visibleCtas,
            )
        }
    }

    fun onOnboardingCompletedToggled(checked: Boolean) {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                if (checked) {
                    userStageStore.moveToStage(AppStage.ESTABLISHED)
                    settingsDataStore.hideTips = false
                    _viewState.value.visibleCtaIds.forEach { ctaId ->
                        dismissedCtaDao.insert(DismissedCta(ctaId))
                    }
                } else {
                    userStageStore.moveToStage(AppStage.DAX_ONBOARDING)
                    settingsDataStore.hideTips = false
                    _viewState.value.visibleCtaIds.forEach { ctaId ->
                        dismissedCtaDao.delete(ctaId)
                    }
                }
                loadState()
            }
        }
    }

    fun onOnboardingSkippedToggled(checked: Boolean) {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                if (checked) {
                    userStageStore.moveToStage(AppStage.ESTABLISHED)
                    settingsDataStore.hideTips = true
                } else {
                    settingsDataStore.hideTips = false
                }
                loadState()
            }
        }
    }

    fun onCtaDismissedToggled(ctaId: CtaId, isDismissed: Boolean) {
        viewModelScope.launch {
            withContext(dispatchers.io()) {
                if (isDismissed) {
                    dismissedCtaDao.insert(DismissedCta(ctaId))
                } else {
                    dismissedCtaDao.delete(ctaId)
                }
                val required = ctaViewModel.requiredDaxOnboardingCtas().toSet()
                val allRequiredDismissed = required.all { dismissedCtaDao.exists(it) }
                val wasCompletedByCtas = _viewState.value.onboardingCompleted && !settingsDataStore.hideTips

                when {
                    ctaId == CtaId.ADD_WIDGET -> { /* noop */ }
                    isDismissed && allRequiredDismissed -> {
                        userStageStore.moveToStage(AppStage.ESTABLISHED)
                        settingsDataStore.hideTips = false
                    }
                    !isDismissed && wasCompletedByCtas && required.contains(ctaId) -> {
                        userStageStore.moveToStage(AppStage.DAX_ONBOARDING)
                    }
                }
                loadState()
            }
        }
    }

    fun isIndependentCta(ctaId: CtaId): Boolean = ctaId == CtaId.ADD_WIDGET
}
