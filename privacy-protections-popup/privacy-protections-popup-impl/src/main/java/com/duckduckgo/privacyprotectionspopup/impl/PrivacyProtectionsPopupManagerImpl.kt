/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupManagerImpl @Inject constructor() : PrivacyProtectionsPopupManager {

    private val _viewState = MutableStateFlow(PrivacyProtectionsPopupViewState(visible = false))

    override val viewState: Flow<PrivacyProtectionsPopupViewState>
        get() = _viewState.asStateFlow()

    override fun onUiEvent(event: PrivacyProtectionsPopupUiEvent) {
        // TODO
    }

    override fun onPageRefreshTriggeredByUser() {
        // TODO
    }

    override fun onPageLoaded(
        url: String,
        httpErrorCodes: List<Int>,
    ) {
        // TODO
    }
}
