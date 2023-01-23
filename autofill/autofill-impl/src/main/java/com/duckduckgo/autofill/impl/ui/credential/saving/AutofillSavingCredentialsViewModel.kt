/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.saving

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.launch

@ContributesViewModel(ActivityScope::class)
class AutofillSavingCredentialsViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    @Inject
    lateinit var autofillStore: AutofillStore

    fun showOnboarding(): Boolean {
        return autofillStore.showOnboardingWhenOfferingToSaveLogin
    }

    fun determineTextResources(credentials: LoginCredentials): DisplayStringResourceIds {
        val title: Int = determineTitle(credentials)
        val ctaButton: Int = determineCtaButtonText(credentials)

        return DisplayStringResourceIds(
            title = title,
            ctaButton = ctaButton,
        )
    }

    @StringRes
    private fun determineTitle(credentials: LoginCredentials): Int {
        if (showOnboarding()) {
            return R.string.saveLoginDialogFirstTimeOnboardingExplanationTitle
        }

        return if (credentials.username == null) {
            R.string.saveLoginMissingUsernameDialogTitle
        } else {
            R.string.saveLoginDialogTitle
        }
    }

    @StringRes
    private fun determineCtaButtonText(credentials: LoginCredentials): Int {
        if (showOnboarding()) {
            return R.string.saveLoginDialogButtonSave
        }

        return if (credentials.username == null) {
            R.string.saveLoginMissingUsernameDialogButtonSave
        } else {
            R.string.saveLoginDialogButtonSave
        }
    }

    fun userPromptedToSaveCredentials() {
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.hasEverBeenPromptedToSaveLogin = true
        }
    }

    data class DisplayStringResourceIds(
        @StringRes val title: Int,
        @StringRes val ctaButton: Int,
    )
}
