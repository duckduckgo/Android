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

package com.duckduckgo.autofill.impl.ui.credential.management

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenDividerBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenHeaderBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowSearchNoResultsBinding
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.CredentialListItem.Credential
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.CredentialListItem.SuggestedCredential
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.Divider
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.NoMatchingSearchResults
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.suggestion.SuggestionListBuilder
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.extractTitle
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.launch

class AutofillManagementRecyclerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val dispatchers: DispatcherProvider,
    private val faviconManager: FaviconManager,
    private val grouper: CredentialGrouper,
    private val initialExtractor: InitialExtractor,
    private val suggestionListBuilder: SuggestionListBuilder,
    private val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
    private val onContextMenuItemClicked: (ContextMenuAction) -> Unit,
) : Adapter<RecyclerView.ViewHolder>() {

    private var listItems = listOf<ListItem>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            ITEM_VIEW_TYPE_HEADER -> {
                val binding = ItemRowAutofillCredentialsManagementScreenHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                HeadingViewHolder(binding)
            }

            ITEM_VIEW_TYPE_CREDENTIAL -> {
                val binding = ItemRowAutofillCredentialsManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                CredentialsViewHolder(binding)
            }

            ITEM_VIEW_TYPE_SUGGESTED_CREDENTIAL -> {
                val binding = ItemRowAutofillCredentialsManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SuggestedCredentialsViewHolder(binding)
            }

            ITEM_VIEW_TYPE_DIVIDER -> {
                val binding = ItemRowAutofillCredentialsManagementScreenDividerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                DividerViewHolder(binding)
            }

            ITEM_VIEW_TYPE_NO_MATCHING_SEARCH_RESULTS -> {
                val binding = ItemRowSearchNoResultsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                NoMatchingSearchResultsViewHolder(binding)
            }

            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(
        viewHolder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (viewHolder) {
            is SuggestedCredentialsViewHolder -> onBindViewHolderSuggestedCredential(position, viewHolder)
            is CredentialsViewHolder -> onBindViewHolderCredential(position, viewHolder)
            is HeadingViewHolder -> onBindViewHolderHeading(position, viewHolder)
            is NoMatchingSearchResultsViewHolder -> onBindViewHolderNoMatchingSearchResults(position, viewHolder)
        }
    }

    private fun onBindViewHolderNoMatchingSearchResults(
        position: Int,
        viewHolder: NoMatchingSearchResultsViewHolder,
    ) {
        val item = listItems[position] as NoMatchingSearchResults
        val formattedNoResultsText = viewHolder.itemView.context.getString(R.string.autofillManagementNoSearchResults, item.query)
        viewHolder.binding.noMatchingLoginsHint.text = formattedNoResultsText
    }

    private fun onBindViewHolderCredential(
        position: Int,
        viewHolder: CredentialsViewHolder,
    ) {
        val item = listItems[position] as Credential
        populateCredentialsDetails(item.credentials, viewHolder)
    }

    private fun onBindViewHolderSuggestedCredential(
        position: Int,
        viewHolder: SuggestedCredentialsViewHolder,
    ) {
        val item = listItems[position] as SuggestedCredential
        populateCredentialsDetails(item.credentials, viewHolder)
    }

    private fun populateCredentialsDetails(
        loginCredentials: LoginCredentials,
        viewHolder: CredentialsViewHolder,
    ) {
        with(viewHolder.binding) {
            title.setPrimaryText(loginCredentials.extractTitle() ?: "")
            title.setSecondaryText(loginCredentials.username ?: "")
            root.setOnClickListener { onCredentialSelected(loginCredentials) }

            val popupMenu = initializePopupMenu(root.context, loginCredentials)
            overflowMenu.setOnClickListener {
                popupMenu.show(root, it)
            }

            updateFavicon(loginCredentials)
        }
    }

    private fun onBindViewHolderHeading(
        position: Int,
        viewHolder: HeadingViewHolder,
    ) {
        val item = listItems[position] as GroupHeading
        with(viewHolder.binding) {
            groupHeader.primaryText = item.label
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is GroupHeading -> ITEM_VIEW_TYPE_HEADER
            is Credential -> ITEM_VIEW_TYPE_CREDENTIAL
            is SuggestedCredential -> ITEM_VIEW_TYPE_SUGGESTED_CREDENTIAL
            is Divider -> ITEM_VIEW_TYPE_DIVIDER
            is NoMatchingSearchResults -> ITEM_VIEW_TYPE_NO_MATCHING_SEARCH_RESULTS
        }
    }

    private fun initializePopupMenu(
        context: Context,
        loginCredentials: LoginCredentials,
    ): PopupMenu {
        return PopupMenu(LayoutInflater.from(context), R.layout.overflow_menu_list_item, width = getPopupMenuWidth(context)).apply {
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_edit)) { onContextMenuItemClicked(Edit(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_delete)) { onContextMenuItemClicked(Delete(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_copy_username)) { onContextMenuItemClicked(CopyUsername(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_copy_password)) { onContextMenuItemClicked(CopyPassword(loginCredentials)) }
        }
    }

    private fun getPopupMenuWidth(context: Context): Int {
        val orientation = context.resources.configuration.orientation
        return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LayoutParams.WRAP_CONTENT
        } else {
            context.resources.getDimensionPixelSize(R.dimen.credentialManagementListItemPopupMenuWidth)
        }
    }

    private fun ItemRowAutofillCredentialsManagementScreenBinding.updateFavicon(credentials: LoginCredentials) {
        lifecycleOwner.lifecycleScope.launch {
            val url = credentials.domain.orEmpty()
            val faviconPlaceholderLetter = initialExtractor.extractInitial(credentials)
            faviconManager.loadToViewMaybeFromRemoteWithPlaceholder(url = url, view = favicon, placeholder = faviconPlaceholderLetter)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateLogins(
        unsortedCredentials: List<LoginCredentials>,
        unsortedDirectSuggestions: List<LoginCredentials>,
        unsortedSharableSuggestions: List<LoginCredentials>,
    ) {
        val newList = mutableListOf<ListItem>()

        val directSuggestionsListItems = suggestionListBuilder.build(unsortedDirectSuggestions, unsortedSharableSuggestions)
        newList.addAll(directSuggestionsListItems)

        val groupedCredentials = grouper.group(unsortedCredentials)
        newList.addAll(groupedCredentials)

        listItems = newList
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun showNoMatchingSearchResults(query: String) {
        listItems = listOf(NoMatchingSearchResults(query))
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = listItems.size

    sealed class ContextMenuAction {
        data class Edit(val credentials: LoginCredentials) : ContextMenuAction()
        data class Delete(val credentials: LoginCredentials) : ContextMenuAction()
        data class CopyUsername(val credentials: LoginCredentials) : ContextMenuAction()
        data class CopyPassword(val credentials: LoginCredentials) : ContextMenuAction()
    }

    sealed interface ListItem {
        sealed class CredentialListItem(open val credentials: LoginCredentials) : ListItem {
            data class Credential(override val credentials: LoginCredentials) : CredentialListItem(credentials)
            data class SuggestedCredential(override val credentials: LoginCredentials) : CredentialListItem(credentials)
        }

        data class GroupHeading(val label: String) : ListItem
        object Divider : ListItem
        data class NoMatchingSearchResults(val query: String) : ListItem
    }

    open class CredentialsViewHolder(open val binding: ItemRowAutofillCredentialsManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
    class SuggestedCredentialsViewHolder(override val binding: ItemRowAutofillCredentialsManagementScreenBinding) : CredentialsViewHolder(binding)
    class HeadingViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class DividerViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenDividerBinding) : RecyclerView.ViewHolder(binding.root)
    class NoMatchingSearchResultsViewHolder(val binding: ItemRowSearchNoResultsBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val ITEM_VIEW_TYPE_HEADER = 0
        private const val ITEM_VIEW_TYPE_CREDENTIAL = 1
        private const val ITEM_VIEW_TYPE_SUGGESTED_CREDENTIAL = 2
        private const val ITEM_VIEW_TYPE_DIVIDER = 3
        private const val ITEM_VIEW_TYPE_NO_MATCHING_SEARCH_RESULTS = 4
    }
}
