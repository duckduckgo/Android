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

package com.duckduckgo.privacyprotectionspopup.api

import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing the UI layer of the Privacy Protections Popup.
 */
interface PrivacyProtectionsPopup {

    /**
     * Updates the view state of the popup.
     *
     * @param viewState The new view state to be set for the popup.
     */
    fun setViewState(viewState: PrivacyProtectionsPopupViewState)

    /**
     * A flow of UI events for the Privacy Protections Popup.
     *
     * This property emits [PrivacyProtectionsPopupUiEvent] objects representing
     * various UI interactions or state changes that occur within the popup.
     * Those events should be consumed by [PrivacyProtectionsPopupManager]
     */
    val events: Flow<PrivacyProtectionsPopupUiEvent>
    fun onConfigurationChanged()
}
