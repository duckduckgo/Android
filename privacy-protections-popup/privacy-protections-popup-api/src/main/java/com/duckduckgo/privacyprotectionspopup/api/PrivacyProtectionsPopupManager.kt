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
 * Interface for managing the business logic associated with the Privacy Protections Popup.
 *
 * It is intended to be used within a ViewModel.
 */
interface PrivacyProtectionsPopupManager {

    /**
     * A flow representing the current view state of the [PrivacyProtectionsPopup].
     */
    val viewState: Flow<PrivacyProtectionsPopupViewState>

    /**
     * Handles UI events emitted by the [PrivacyProtectionsPopup].
     *
     * @param event The [PrivacyProtectionsPopupUiEvent] to be handled.
     */
    fun onUiEvent(event: PrivacyProtectionsPopupUiEvent)

    /**
     * Invoked when a page refresh is triggered by the user.
     *
     * This function should be called whenever the user triggers page refresh,
     * either by the pull-to-refresh gesture or the button in the menu.
     */
    fun onPageRefreshTriggeredByUser()

    /**
     * Handles the event of a page being fully loaded.
     *
     * @param url The URL of the loaded page.
     * @param httpErrorCodes A list of HTTP error codes encountered during the page load.
     * @param hasBrowserError Boolean indicating whether a browser error occurred.
     */
    fun onPageLoaded(
        url: String,
        httpErrorCodes: List<Int>,
        hasBrowserError: Boolean,
    )
}
