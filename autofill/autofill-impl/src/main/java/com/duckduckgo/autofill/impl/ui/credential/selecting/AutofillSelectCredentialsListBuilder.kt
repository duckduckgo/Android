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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.content.Context
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.CredentialPrimaryType
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.CredentialSecondaryType
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.VerticalSpacing
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillSelectCredentialsListBuilder {
    fun buildFlatList(sortedGroups: Groups): List<ListItem>
}

@ContributesBinding(FragmentScope::class)
class RealAutofillSelectCredentialsListBuilder @Inject constructor(
    private val context: Context,
) : AutofillSelectCredentialsListBuilder {

    override fun buildFlatList(sortedGroups: Groups): List<ListItem> {
        val flattenedList = mutableListOf<ListItem>()

        if (sortedGroups.perfectMatches.isNotEmpty()) {
            processPerfectMatches(sortedGroups, flattenedList)
        }

        // if we had any perfect matches we'll show the primary button there. So only show it for partial matches where there are no perfect matches.
        var addPrimaryButtonToPartialMatches = sortedGroups.perfectMatches.isEmpty()

        sortedGroups.partialMatches.forEach { (key, value) ->
            val addedPrimaryButton = processPartialMatches(flattenedList, key, value, addPrimaryButtonToPartialMatches)
            if (addedPrimaryButton) {
                // only add the primary button once
                addPrimaryButtonToPartialMatches = false
            }
        }

        // we don't want to show primary button for shareable credentials as we have no way of knowing which is best
        sortedGroups.shareableCredentials.forEach { (key, value) ->
            processPartialMatches(flattenedList, key, value, usePrimaryButtonType = false)
        }

        return flattenedList
    }

    private fun processPerfectMatches(
        sortedGroups: Groups,
        flattenedList: MutableList<ListItem>,
    ) {
        // Conditionally show "From This Website" grouping, only if there are other groups to show
        if (sortedGroups.partialMatches.isNotEmpty()) {
            flattenedList.add(GroupHeading(context.getString(R.string.useSavedLoginDialogGroupThisWebsite)))
        } else {
            // else add spacing that the group heading would have taken up
            flattenedList.add(VerticalSpacing)
        }
        sortedGroups.perfectMatches.forEachIndexed { index, loginCredentials ->
            if (index == 0) {
                flattenedList.add(CredentialPrimaryType(loginCredentials))
            } else {
                flattenedList.add(CredentialSecondaryType(loginCredentials))
            }
        }
    }

    /**
     * Process the partial matches for a given domain, adding them to the flattened list
     * @param usePrimaryButtonType if true, the primary button will be used for the first item in the list
     * @return true if the primary button was used, false otherwise
     */
    private fun processPartialMatches(
        flattenedList: MutableList<ListItem>,
        key: String,
        value: List<LoginCredentials>,
        usePrimaryButtonType: Boolean,
    ): Boolean {
        flattenedList.add(GroupHeading(context.getString(R.string.useSavedLoginDialogFromDomainLabel, key)))

        var primaryButtonAdded = false
        value.forEachIndexed { index, loginCredentials ->
            if (index == 0 && usePrimaryButtonType) {
                flattenedList.add(CredentialPrimaryType(loginCredentials))
                primaryButtonAdded = true
            } else {
                flattenedList.add(CredentialSecondaryType(loginCredentials))
            }
        }

        return primaryButtonAdded
    }
}
