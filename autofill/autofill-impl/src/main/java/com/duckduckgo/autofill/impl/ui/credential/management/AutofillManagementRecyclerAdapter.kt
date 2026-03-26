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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.text.toSpanned
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
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillEmptyStateManagementScreenBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillReportBreakageManagementScreenBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowAutofillToggleSectionBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowPromoAutofillManagementScreenBinding
import com.duckduckgo.autofill.impl.databinding.ItemRowSearchNoResultsBinding
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyPassword
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.CopyUsername
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Delete
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.ContextMenuAction.Edit
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.CredentialsLoadedState.Loaded
import com.duckduckgo.autofill.impl.ui.credential.management.AutofillManagementRecyclerAdapter.CredentialsLoadedState.Loading
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.InitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.extractTitle
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.CredentialListItem.Credential
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.CredentialListItem.SuggestedCredential
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.Divider
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.EmptyStateView
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.NoMatchingSearchResults
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.PromotionContainer
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.ReportAutofillBreakage
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.TopLevelControls
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.addClickableLink
import com.duckduckgo.common.ui.view.prependIconToText
import com.duckduckgo.common.ui.view.text.DaxTextView
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

class AutofillManagementRecyclerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val faviconManager: FaviconManager,
    private val initialExtractor: InitialExtractor,
    private val onCredentialSelected: (credentials: LoginCredentials) -> Unit,
    private val onContextMenuItemClicked: ((ContextMenuAction) -> Unit)?,
    private val onReportBreakageClicked: () -> Unit,
    private val launchHelpPageClicked: () -> Unit,
    private val onAutofillToggleClicked: (isChecked: Boolean) -> Unit,
    private val onImportFromGoogleClicked: () -> Unit,
    private val onImportViaDesktopSyncClicked: () -> Unit,
) : Adapter<RecyclerView.ViewHolder>() {

    private var listItems = listOf<ListItem>()

    private val globalAutofillToggleListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        onAutofillToggleClicked(isChecked)
    }

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

            ITEM_VIEW_TYPE_REPORT_AUTOFILL_BREAKAGE -> {
                val binding = ItemRowAutofillReportBreakageManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                ReportBreakageViewHolder(binding)
            }

            ITEM_VIEW_TYPE_AUTOFILL_TOGGLE -> {
                val binding = ItemRowAutofillToggleSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                AutofillToggleViewHolder(binding)
            }

            ITEM_VIEW_TYPE_PROMO_CARD -> {
                val binding = ItemRowPromoAutofillManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                PromoCardViewHolder(binding)
            }

            ITEM_VIEW_TYPE_EMPTY_STATE -> {
                val binding = ItemRowAutofillEmptyStateManagementScreenBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                EmptyStateViewHolder(binding)
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
            is ReportBreakageViewHolder -> onBindViewHolderReportBreakage(viewHolder)
            is AutofillToggleViewHolder -> onBindViewHolderAutofillToggle(position, viewHolder)
            is DividerViewHolder -> onBindViewHolderDivider()
            is PromoCardViewHolder -> onBindPromoCardViewHolder(position, viewHolder)
            is EmptyStateViewHolder -> onBindEmptyStateViewHolder(position, viewHolder)
            else -> throw IllegalArgumentException("Unknown view holder ${viewHolder.javaClass.simpleName}")
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

    private fun onBindViewHolderReportBreakage(viewHolder: ReportBreakageViewHolder) {
        viewHolder.binding.root.setOnClickListener {
            onReportBreakageClicked()
        }
    }

    private fun onBindViewHolderAutofillToggle(
        position: Int,
        viewHolder: AutofillToggleViewHolder,
    ) {
        configureInfoText(viewHolder.binding.infoText, viewHolder.itemView)
        with(viewHolder.binding.enabledToggle) {
            val item = listItems[position] as TopLevelControls
            quietlySetIsChecked(item.initialToggleStateIsEnabled, globalAutofillToggleListener)
            setOnCheckedChangeListener(globalAutofillToggleListener)
        }
    }

    private fun onBindPromoCardViewHolder(
        position: Int,
        viewHolder: PromoCardViewHolder,
    ) {
        val item = listItems[position] as PromotionContainer
        with(viewHolder.binding.promotionContainer) {
            removeAllViews()
            addView(item.promotionView)
        }
    }

    private fun onBindEmptyStateViewHolder(
        position: Int,
        viewHolder: EmptyStateViewHolder,
    ) {
        val item = listItems[position] as EmptyStateView
        with(viewHolder.binding) {
            importPasswordsFromGoogleButton.visibility = if (item.showGoogleImportButton) View.VISIBLE else View.GONE
            importPasswordsFromGoogleButton.setOnClickListener { onImportFromGoogleClicked() }

            importPasswordsViaDesktopSyncButton.setOnClickListener { onImportViaDesktopSyncClicked() }
        }
    }

    private fun onBindViewHolderDivider() {
        // no-op
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
            if (onContextMenuItemClicked != null) {
                title.setTrailingIconClickListener { anchor ->
                    val overflowMenu = initializePopupMenu(root.context, loginCredentials, onContextMenuItemClicked)
                    overflowMenu.show(root, anchor)
                }
            } else {
                title.hideTrailingItems()
            }
            root.setOnClickListener { onCredentialSelected(loginCredentials) }

            updateFavicon(loginCredentials)
        }
    }

    private fun configureInfoText(
        infoText: DaxTextView,
        root: View,
    ) {
        infoText.addClickableLink(
            annotation = "learn_more_link",
            textSequence = root.context.prependIconToText(
                R.string.credentialManagementAutofillSubtitle,
                R.drawable.ic_lock_solid_12,
            ).toSpanned(),
            onClick = launchHelpPageClicked,
        )
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
            is ReportAutofillBreakage -> ITEM_VIEW_TYPE_REPORT_AUTOFILL_BREAKAGE
            is TopLevelControls -> ITEM_VIEW_TYPE_AUTOFILL_TOGGLE
            is PromotionContainer -> ITEM_VIEW_TYPE_PROMO_CARD
            is EmptyStateView -> ITEM_VIEW_TYPE_EMPTY_STATE
        }
    }

    private fun initializePopupMenu(
        context: Context,
        loginCredentials: LoginCredentials,
        onContextMenuItemClicked: (ContextMenuAction) -> Unit,
    ): PopupMenu {
        return PopupMenu(LayoutInflater.from(context), R.layout.overflow_menu_list_item).apply {
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_edit)) { onContextMenuItemClicked(Edit(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_overflow_delete)) { onContextMenuItemClicked(Delete(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_copy_username)) { onContextMenuItemClicked(CopyUsername(loginCredentials)) }
            onMenuItemClicked(contentView.findViewById(R.id.item_copy_password)) { onContextMenuItemClicked(CopyPassword(loginCredentials)) }
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
    fun showLogins(
        autofillToggleState: AutofillToggleState,
        promotionView: View?,
        credentialsLoadedState: CredentialsLoadedState,
    ) {
        logcat(VERBOSE) {
            """
                Updating logins,
                credentialsLoadedState=$credentialsLoadedState,
                promo view: ${promotionView?.javaClass?.simpleName}
            """.trimIndent()
        }

        val newList = mutableListOf<ListItem>()

        promotionView?.let {
            newList.addPromotionView(promotionView)
        }

        if (autofillToggleState.visible) {
            newList.addTopLevelControls(autofillToggleState.enabled)
        }

        when (credentialsLoadedState) {
            is Loading -> {
                // no-op
            }
            is Loaded -> {
                if (credentialsLoadedState.groupedCredentials.isNotEmpty()) {
                    newList.addAll(credentialsLoadedState.directSuggestionsListItems)
                    newList.addAll(credentialsLoadedState.groupedCredentials)
                } else {
                    newList.addEmptyStateView(credentialsLoadedState.showGoogleImportPasswordsButton)
                }
            }
        }

        listItems = newList
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun showNoMatchingSearchResults(query: String) {
        listItems = listOf(NoMatchingSearchResults(query))
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = listItems.size

    private fun MutableList<ListItem>.addTopLevelControls(autofillToggleEnabled: Boolean) {
        add(TopLevelControls(autofillToggleEnabled))
    }

    private fun MutableList<ListItem>.addPromotionView(promotionView: View) {
        add(PromotionContainer(promotionView))
    }

    private fun MutableList<ListItem>.addEmptyStateView(canImportGooglePasswords: Boolean) {
        add(EmptyStateView(canImportGooglePasswords))
    }

    sealed class ContextMenuAction {
        data class Edit(val credentials: LoginCredentials) : ContextMenuAction()
        data class Delete(val credentials: LoginCredentials) : ContextMenuAction()
        data class CopyUsername(val credentials: LoginCredentials) : ContextMenuAction()
        data class CopyPassword(val credentials: LoginCredentials) : ContextMenuAction()
    }

    open class CredentialsViewHolder(open val binding: ItemRowAutofillCredentialsManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
    class SuggestedCredentialsViewHolder(override val binding: ItemRowAutofillCredentialsManagementScreenBinding) : CredentialsViewHolder(binding)
    class HeadingViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class DividerViewHolder(val binding: ItemRowAutofillCredentialsManagementScreenDividerBinding) : RecyclerView.ViewHolder(binding.root)
    class ReportBreakageViewHolder(val binding: ItemRowAutofillReportBreakageManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
    class NoMatchingSearchResultsViewHolder(val binding: ItemRowSearchNoResultsBinding) : RecyclerView.ViewHolder(binding.root)
    class AutofillToggleViewHolder(val binding: ItemRowAutofillToggleSectionBinding) : RecyclerView.ViewHolder(binding.root)
    class PromoCardViewHolder(val binding: ItemRowPromoAutofillManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)
    class EmptyStateViewHolder(val binding: ItemRowAutofillEmptyStateManagementScreenBinding) : RecyclerView.ViewHolder(binding.root)

    data class AutofillToggleState(
        val enabled: Boolean,
        val visible: Boolean,
    )

    sealed interface CredentialsLoadedState {
        data class Loaded(
            val directSuggestionsListItems: Collection<ListItem>,
            val groupedCredentials: Collection<ListItem>,
            val showGoogleImportPasswordsButton: Boolean,
        ) : CredentialsLoadedState

        data object Loading : CredentialsLoadedState
    }

    companion object {
        private const val ITEM_VIEW_TYPE_HEADER = 0
        private const val ITEM_VIEW_TYPE_CREDENTIAL = 1
        private const val ITEM_VIEW_TYPE_SUGGESTED_CREDENTIAL = 2
        private const val ITEM_VIEW_TYPE_DIVIDER = 3
        private const val ITEM_VIEW_TYPE_NO_MATCHING_SEARCH_RESULTS = 4
        private const val ITEM_VIEW_TYPE_REPORT_AUTOFILL_BREAKAGE = 5

        private const val ITEM_VIEW_TYPE_AUTOFILL_TOGGLE = 6
        private const val ITEM_VIEW_TYPE_PROMO_CARD = 7
        private const val ITEM_VIEW_TYPE_EMPTY_STATE = 8
    }
}
