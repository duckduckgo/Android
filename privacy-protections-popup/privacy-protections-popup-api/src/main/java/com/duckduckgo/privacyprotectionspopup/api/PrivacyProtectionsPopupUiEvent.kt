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

/**
 * Enum class representing different UI events emitted by [PrivacyProtectionsPopup]
 * and consumed by [PrivacyProtectionsPopupManager].
 */
enum class PrivacyProtectionsPopupUiEvent {
    /**
     * Event indicating that the popup was dismissed by the user without explicit
     * interaction with the popup UI, e.g., by clicking outside of the popup.
     */
    DISMISSED,

    /**
     * Event indicating that the dismiss button within the popup was clicked.
     */
    DISMISS_CLICKED,

    /**
     * Event indicating that the 'Disable Protections' button was clicked.
     */
    DISABLE_PROTECTIONS_CLICKED,

    /**
     * Event indicating that the 'Don't show again' button was clicked.
     */
    DONT_SHOW_AGAIN_CLICKED,

    /**
     * Event indicating that the privacy dashboard icon (popup anchor view) was clicked.
     *
     * The click event is passed to the popup anchor view, so no extra handling is necessary
     * to open the privacy dashboard. This is emitted to ensure the [PrivacyProtectionsPopupManager]
     * state is updated and for measurement purposes.
     */
    PRIVACY_DASHBOARD_CLICKED,
}
