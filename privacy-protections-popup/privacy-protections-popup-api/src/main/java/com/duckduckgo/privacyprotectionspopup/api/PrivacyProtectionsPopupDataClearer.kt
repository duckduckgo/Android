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
 * Interface for clearing any personal data that may be managed by Privacy Protections Popup.
 * Should be used whenever the "fire" action is invoked.
 */
interface PrivacyProtectionsPopupDataClearer {

    /**
     * Deletes any personal data managed by this module (e.g., domains, for which the popup was shown).
     */
    suspend fun clearPersonalData()
}
