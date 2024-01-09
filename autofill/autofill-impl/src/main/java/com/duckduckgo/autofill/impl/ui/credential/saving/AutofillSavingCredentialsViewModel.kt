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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesViewModel(ActivityScope::class)
class AutofillSavingCredentialsViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
) : ViewModel() {

    @Inject
    lateinit var autofillStore: AutofillStore

    fun userPromptedToSaveCredentials() {
        viewModelScope.launch(dispatchers.io()) {
            autofillStore.hasEverBeenPromptedToSaveLogin = true
        }
    }

    fun addSiteToNeverSaveList(originalUrl: String) {
        Timber.d("User selected to never save for this site %s", originalUrl)
        viewModelScope.launch(dispatchers.io()) {
            neverSavedSiteRepository.addToNeverSaveList(originalUrl)
        }
        pixel.fire(AUTOFILL_NEVER_SAVE_FOR_THIS_SITE_USER_SELECTED_FROM_SAVE_DIALOG)
    }
}
