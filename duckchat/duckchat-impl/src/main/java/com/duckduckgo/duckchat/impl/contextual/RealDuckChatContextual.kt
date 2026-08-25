/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.contextual

import android.app.Activity
import android.content.ContextWrapper
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChatContextual
import com.duckduckgo.duckchat.api.DuckChatEntryPoint
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.store.DuckChatContextualDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckChatContextual @Inject constructor(
    private val duckChatInternal: DuckChatInternal,
    private val browserNav: BrowserNav,
    private val contextualDataStore: DuckChatContextualDataStore,
    private val sessionTimeoutProvider: DuckChatContextualSessionTimeoutProvider,
    private val timeProvider: DuckChatContextualTimeProvider,
) : DuckChatContextual {

    override suspend fun launch(
        sourceTabId: String,
        anchor: View?,
        onAskAboutPage: () -> Unit,
    ) {
        if (anchor == null || !duckChatInternal.isContextualSheetRedesignEnabled()) {
            onAskAboutPage()
            return
        }
        if (hasChatInProgress(sourceTabId)) {
            // The sheet would reopen the existing chat for this tab, so skip the entry menu and open it directly.
            onAskAboutPage()
        } else {
            showMenu(sourceTabId, anchor, onAskAboutPage)
        }
    }

    private suspend fun hasChatInProgress(tabId: String): Boolean {
        if (contextualDataStore.getTabChatUrl(tabId).isNullOrBlank()) return false
        return shouldReuseStoredChatUrl(tabId)
    }

    private suspend fun shouldReuseStoredChatUrl(tabId: String): Boolean {
        val lastClosedTimestamp = contextualDataStore.getTabClosedTimestamp(tabId) ?: return true
        val timeoutMs = sessionTimeoutProvider.sessionTimeoutMillis()
        if (timeoutMs <= 0) return false
        return timeProvider.currentTimeMillis() - lastClosedTimestamp <= timeoutMs
    }

    override fun createSheet(tabId: String): Fragment {
        return DuckChatContextualFragment().apply {
            arguments = Bundle().apply {
                putString(DuckChatContextualFragment.KEY_DUCK_AI_CONTEXTUAL_TAB_ID, tabId)
            }
        }
    }

    private fun showMenu(
        sourceTabId: String,
        anchor: View,
        onAskAboutPage: () -> Unit,
    ) {
        val activity = anchor.activity() ?: return
        val popup = PopupMenu(LayoutInflater.from(activity), R.layout.popup_contextual_chat_menu)
        val content = popup.contentView
        popup.onMenuItemClicked(content.findViewById(R.id.contextualChatMenuNewChat)) { openNewChatTab(activity, sourceTabId) }
        popup.onMenuItemClicked(content.findViewById(R.id.contextualChatMenuAskAboutPage)) { onAskAboutPage() }
        popup.showAnchoredView(activity, anchor.rootView, anchor)
    }

    private fun openNewChatTab(activity: Activity, sourceTabId: String) {
        val url = duckChatInternal.getDuckChatUrl(query = "", autoPrompt = false)
        duckChatInternal.reportDuckChatEntry(DuckChatEntryPoint.CONTEXTUAL_CHAT, opensNewTab = true, hasPrompt = false)
        browserNav.openInNewTab(activity, url, sourceTabId).also { activity.startActivity(it) }
    }

    private fun View.activity(): Activity? {
        var context = context
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }
}
