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

package com.duckduckgo.autofill.impl.ui.credential.management.suggestion

import android.content.Context
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R.string
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorter
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.CredentialListItem.SuggestedCredential
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.Divider
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.GroupHeading
import javax.inject.Inject

class SuggestionListBuilder @Inject constructor(
    private val context: Context,
    private val sorter: CredentialListSorter,
) {

    fun build(
        unsortedQuerySuggestions: List<LoginCredentials>,
        unsortedDirectSuggestions: List<LoginCredentials>,
        unsortedSharableSuggestions: List<LoginCredentials>,
        allowBreakageReporting: Boolean,
    ): List<ListItem> {
        val list = mutableListOf<ListItem>()

        if (unsortedQuerySuggestions.isNotEmpty() || unsortedDirectSuggestions.isNotEmpty() || unsortedSharableSuggestions.isNotEmpty()) {
            list.add(GroupHeading(context.getString(string.credentialManagementSuggestionsLabel)))

            val sortedQuerySuggestions = sorter.sort(unsortedQuerySuggestions)
            val sortedDirectSuggestions = sorter.sort(unsortedDirectSuggestions)
            val sortedSharableSuggestions = sorter.sort(unsortedSharableSuggestions)

            val allSuggestions = (sortedQuerySuggestions + sortedDirectSuggestions + sortedSharableSuggestions).distinct()
            list.addAll(allSuggestions.map { SuggestedCredential(it) })

            if (allowBreakageReporting) {
                list.add(ListItem.ReportAutofillBreakage)
            }

            list.add(Divider)
        }

        return list
    }
}
