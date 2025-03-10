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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing.list

import android.view.View
import com.duckduckgo.autofill.api.domain.app.LoginCredentials

sealed interface ListItem {
    sealed class CredentialListItem(open val credentials: LoginCredentials) : ListItem {
        data class Credential(override val credentials: LoginCredentials) : CredentialListItem(credentials)
        data class SuggestedCredential(override val credentials: LoginCredentials) : CredentialListItem(credentials)
    }

    data object ReportAutofillBreakage : ListItem
    data class GroupHeading(val label: String) : ListItem
    data object Divider : ListItem
    data class NoMatchingSearchResults(val query: String) : ListItem
    data class TopLevelControls(val initialToggleStateIsEnabled: Boolean) : ListItem
    data class PromotionContainer(val promotionView: View) : ListItem
    data class EmptyStateView(val showGoogleImportButton: Boolean) : ListItem
}
