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

package com.duckduckgo.duckchat.impl.clienttools

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.ui.view.dialog.DaxAlertDialog
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiToolPermissionPrompts
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealDuckAiToolPermissionPrompts @Inject constructor(
    private val prompt: PublishingClientToolPermissionPrompt,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
) : DuckAiToolPermissionPrompts {

    override fun register(
        fragment: Fragment,
        tabId: String,
    ) {
        register(fragment, flowOf(tabId), contextual = false)
    }

    /** As [register], for the contextual sheet, whose page's tab changes while the sheet's fragment stays. */
    fun register(
        fragment: Fragment,
        tabIds: Flow<String>,
        contextual: Boolean,
    ) {
        fragment.viewLifecycleOwnerLiveData.observe(fragment) { owner ->
            owner?.lifecycleScope?.launch { showPrompts(fragment.requireActivity(), tabIds, contextual) }
        }
    }

    /** The open prompt of the chat in the latest of [tabIds] while that tab is the selected one, and null otherwise. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun prompts(
        tabIds: Flow<String>,
        contextual: Boolean,
    ): Flow<PendingClientToolPermission?> =
        tabIds
            .distinctUntilChanged()
            .flatMapLatest { tabId ->
                if (tabId.isEmpty()) flowOf(null) else prompt.pendingFor(tabId, contextual).whileSelected(tabId)
            }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<PendingClientToolPermission?>.whileSelected(tabId: String): Flow<PendingClientToolPermission?> =
        flatMapLatest { pending ->
            if (pending == null) {
                flowOf(null)
            } else {
                tabRepositoryProvider.value.forMode(pending.request.browserMode).flowSelectedTab
                    .map { selected -> pending.takeIf { selected?.tabId == tabId && it.isOpen } }
            }
        }

    private suspend fun showPrompts(
        activity: Activity,
        tabIds: Flow<String>,
        contextual: Boolean,
    ) {
        var shown: Pair<PendingClientToolPermission, DaxAlertDialog>? = null
        try {
            prompts(tabIds, contextual).collect { pending ->
                if (pending === shown?.first) return@collect
                shown?.second?.dismiss()
                shown = pending?.let { it to show(activity, it) }
            }
        } finally {
            shown?.second?.dismiss()
        }
    }

    private fun show(
        activity: Activity,
        pending: PendingClientToolPermission,
    ): DaxAlertDialog = BrowserToolsPermissionsDialog(
        activity = activity,
        request = pending.request,
        selection = { pending.selection },
        onToggle = pending::toggle,
        onAnswer = pending::answer,
    ).also { it.show() }
}
