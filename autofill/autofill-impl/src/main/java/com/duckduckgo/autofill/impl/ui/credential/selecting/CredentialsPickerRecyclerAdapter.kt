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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerDomainDividerBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerPrimaryButtonBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerSecondaryButtonBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsPickerVerticalSpacingBinding
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.CredentialPrimaryType
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.CredentialSecondaryType
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.VerticalSpacing

class CredentialsPickerRecyclerAdapter(
    val lifecycleOwner: LifecycleOwner,
    val faviconManager: FaviconManager,
    val credentialTextExtractor: CredentialTextExtractor,
    private val listItems: List<ListItem>,
    private val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
) : Adapter<ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is CredentialPrimaryType -> ITEM_VIEW_TYPE_CREDENTIAL_PRIMARY
            is CredentialSecondaryType -> ITEM_VIEW_TYPE_CREDENTIAL_SECONDARY
            is GroupHeading -> ITEM_VIEW_TYPE_DOMAIN_DIVIDER
            is VerticalSpacing -> ITEM_VIEW_TYPE_VERTICAL_SPACING
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_CREDENTIAL_PRIMARY -> {
                val binding = ItemRowAutofillCredentialsPickerPrimaryButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                PrimaryTypeCredentialsViewHolder(binding)
            }

            ITEM_VIEW_TYPE_CREDENTIAL_SECONDARY -> {
                val binding = ItemRowAutofillCredentialsPickerSecondaryButtonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SecondaryTypeCredentialsViewHolder(binding)
            }

            ITEM_VIEW_TYPE_DOMAIN_DIVIDER -> {
                val binding = ItemRowAutofillCredentialsPickerDomainDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeadingViewHolder(binding)
            }

            ITEM_VIEW_TYPE_VERTICAL_SPACING -> {
                val binding = ItemRowAutofillCredentialsPickerVerticalSpacingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                VerticalSpacingViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        position: Int,
    ) {
        when (viewHolder) {
            is PrimaryTypeCredentialsViewHolder -> viewHolder.binding.onBindCredentials(listItems[position] as CredentialPrimaryType)
            is SecondaryTypeCredentialsViewHolder -> viewHolder.binding.onBindCredentials(listItems[position] as CredentialSecondaryType)
            is HeadingViewHolder -> viewHolder.binding.onBindHeading(listItems[position] as GroupHeading)
        }
    }

    override fun getItemCount(): Int = listItems.size

    private fun ItemRowAutofillCredentialsPickerDomainDividerBinding.onBindHeading(heading: GroupHeading) {
        this.sectionHeader.text = heading.label
    }

    private fun ItemRowAutofillCredentialsPickerPrimaryButtonBinding.onBindCredentials(credentials: CredentialPrimaryType) {
        useCredentialPrimaryButton.text = credentialTextExtractor.usernameOrPlaceholder(credentials.credentials)
        useCredentialPrimaryButton.setOnClickListener {
            onCredentialSelected(credentials.credentials)
        }
    }

    private fun ItemRowAutofillCredentialsPickerSecondaryButtonBinding.onBindCredentials(credentials: CredentialSecondaryType) {
        useCredentialSecondaryButton.text = credentialTextExtractor.usernameOrPlaceholder(credentials.credentials)
        useCredentialSecondaryButton.setOnClickListener {
            onCredentialSelected(credentials.credentials)
        }
    }

    class PrimaryTypeCredentialsViewHolder(val binding: ItemRowAutofillCredentialsPickerPrimaryButtonBinding) : ViewHolder(binding.root)
    class SecondaryTypeCredentialsViewHolder(val binding: ItemRowAutofillCredentialsPickerSecondaryButtonBinding) : ViewHolder(binding.root)
    class HeadingViewHolder(val binding: ItemRowAutofillCredentialsPickerDomainDividerBinding) : ViewHolder(binding.root)
    class VerticalSpacingViewHolder(val binding: ItemRowAutofillCredentialsPickerVerticalSpacingBinding) : ViewHolder(binding.root)

    companion object {
        private const val ITEM_VIEW_TYPE_CREDENTIAL_PRIMARY = 0
        private const val ITEM_VIEW_TYPE_CREDENTIAL_SECONDARY = 1
        private const val ITEM_VIEW_TYPE_DOMAIN_DIVIDER = 2
        private const val ITEM_VIEW_TYPE_VERTICAL_SPACING = 3
    }

    sealed interface ListItem {
        data class CredentialPrimaryType(val credentials: LoginCredentials) : ListItem
        data class CredentialSecondaryType(val credentials: LoginCredentials) : ListItem
        data class GroupHeading(val label: String) : ListItem
        data object VerticalSpacing : ListItem
    }
}
