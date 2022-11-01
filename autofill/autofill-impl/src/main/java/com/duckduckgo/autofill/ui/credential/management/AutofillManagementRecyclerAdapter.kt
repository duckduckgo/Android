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

package com.duckduckgo.autofill.ui.credential.management

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillCredentialsManagementScreenHeaderBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import kotlinx.coroutines.launch

class AutofillManagementRecyclerAdapter(
    val lifecycleOwner: LifecycleOwner,
    val faviconManager: FaviconManager,
    val grouper: CredentialGrouper,
    val titleExtractor: LoginCredentialTitleExtractor,
    val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
    val onContextMenuItemClicked: (ContextMenuAction) -> Unit,
    val onCopyUsername: (credentials: LoginCredentials) -> Unit,
    val onCopyPassword: (credentials: LoginCredentials) -> Unit
) : Adapter<RecyclerView.ViewHolder>() {

    private var listItems = listOf<ListItem>()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
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
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        when (viewHolder) {
            is CredentialsViewHolder -> onBindViewHolderCredential(position, viewHolder)
            is HeadingViewHolder -> onBindViewHolderHeading(position, viewHolder)
        }
    }

    private fun onBindViewHolderCredential(position: Int, viewHolder: CredentialsViewHolder) {
        val item = listItems[position] as ListItem.Credential
        with(viewHolder.binding) {
            title.text = titleExtractor.extract(item.credentials)
            subtitle.text = item.credentials.username
            root.setOnClickListener { onCredentialSelected(item.credentials) }

            val popupMenu = initializePopupMenu(root.context, item.credentials)
            overflowMenu.setOnClickListener {
                popupMenu.show(root, it)
            }

            updateFavicon(item.credentials)
        }
    }

    private fun onBindViewHolderHeading(position: Int, viewHolder: HeadingViewHolder) {
        val item = listItems[position] as ListItem.GroupHeading
        with(viewHolder.binding) {
            groupHeader.text = item.initial
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (listItems[position]) {
            is ListItem.GroupHeading -> ITEM_VIEW_TYPE_HEADER
            is ListItem.Credential -> ITEM_VIEW_TYPE_CREDENTIAL
        }
    }

    private fun initializePopupMenu(context: Context, loginCredentials: LoginCredentials): PopupMenu {
        return PopupMenu(LayoutInflater.from(context), R.layout.overflow_menu_list_item).apply {
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_edit)) { onContextMenuItemClicked(Edit(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_delete)) { onContextMenuItemClicked(Delete(loginCredentials)) }
        }
    }

    private fun ItemRowAutofillCredentialsManagementScreenBinding.updateFavicon(credentials: LoginCredentials) {
        val domain = credentials.domain
        if (domain == null) {
            favicon.setImageBitmap(null)
        } else {
            lifecycleOwner.lifecycleScope.launch {
                faviconManager.loadToViewFromLocalOrFallback(url = domain, view = favicon)
            }
        }
    }

    fun updateLogins(unsortedCredentials: List<LoginCredentials>) {
        val groupedCredentials = grouper.group(unsortedCredentials)
        listItems = groupedCredentials
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = listItems.size

    sealed class ListItem {
        data class Credential(val credentials: LoginCredentials) : ListItem()
        data class GroupHeading(val initial: String) : ListItem()
    }

    sealed class ContextMenuAction {
        data class Edit(val credentials: LoginCredentials) : ContextMenuAction()
        data class Delete(val credentials: LoginCredentials) : ContextMenuAction()
    }
    companion object {
        private const val ITEM_VIEW_TYPE_HEADER = 0
        private const val ITEM_VIEW_TYPE_CREDENTIAL = 1
    }

    class CredentialsViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
    class HeadingViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenHeaderBinding) : RecyclerView.ViewHolder(binding.root)
}
