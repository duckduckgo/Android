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
import android.view.LayoutInflater
import android.view.View
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.ui.view.button.ButtonType.SECONDARY
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.StackedButton
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ItemClientToolGrantAccessBinding
import com.duckduckgo.duckchat.impl.databinding.ViewClientToolGrantAccessBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Shows the pending client tool prompt in the browser while its chat's tab is the selected one.
 * Both chat surfaces, the Duck.ai tab and the contextual sheet, live in the browser activity.
 */
@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
class ClientToolPermissionDialogPresenter @Inject constructor(
    private val prompt: PublishingClientToolPermissionPrompt,
    // Lazy, as in RealNativeInputStateStore: TabDataRepository injects duckchat bindings itself.
    private val tabRepositoryProvider: Lazy<BrowserModeDataProvider<TabRepository>>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ActivityLifecycleCallbacks {

    private var observing: Job? = null
    private var shown: ShownDialog? = null
    private val selections = GrantAccessSelections()

    override fun onActivityResumed(activity: Activity) {
        if (activity.javaClass.name != BROWSER_ACTIVITY) return
        observing?.cancel()
        observing = appCoroutineScope.launch(dispatcherProvider.main()) {
            visiblePrompt(prompt.pending, ::selectedTabId).collect { show(activity, it) }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity.javaClass.name != BROWSER_ACTIVITY) return
        observing?.cancel()
        observing = null
        // Withdrawn from the screen only: the prompt stays pending and comes back on the next resume.
        show(activity, null)
    }

    private fun selectedTabId(mode: BrowserMode): Flow<String?> =
        tabRepositoryProvider.value.forMode(mode).flowSelectedTab.map { it?.tabId }

    private fun show(
        activity: Activity,
        pending: PendingClientToolPermission?,
    ) {
        if (shown?.pending === pending) return
        shown?.dialog?.dismiss()
        shown = null
        if (pending == null || !pending.isOpen) return

        val dialog = StackedAlertDialogBuilder(activity)
            .setRebrandUpdate(true)
            .setCancellable(true)
            .setHeaderImageResource(R.drawable.duck_ai_color_24)
        when (val request = pending.request) {
            is ClientToolPermissionRequest.RunTool ->
                dialog
                    .setTitle(request.tool.copy.promptTitle)
                    .setMessage(request.tool.copy.promptMessage)
            is ClientToolPermissionRequest.RunToolOnce ->
                dialog
                    .setTitle(request.copy.promptTitle)
                    .setMessage(activity.getString(request.copy.promptMessage, request.detail))
            is ClientToolPermissionRequest.GrantAccess ->
                dialog
                    .setTitle(R.string.duckAiClientToolRequestAccessPromptTitle)
                    .setMessage(R.string.duckAiClientToolRequestAccessPromptMessage)
                    .setContentView(grantAccessContent(activity, pending))
        }
        dialog
            .setStackedButtons(
                listOf(
                    StackedButton(allowButtonText(pending.request), SECONDARY),
                    StackedButton(R.string.duckAiClientToolPromptDontAllow, SECONDARY),
                ),
            )
            .addEventListener(
                object : StackedAlertDialogBuilder.EventListener() {
                    override fun onButtonClicked(position: Int) = pending.answer(
                        when (position) {
                            ALLOW_BUTTON -> allowAnswer(pending)
                            else -> ClientToolPermissionAnswer.DontAllow
                        },
                    )

                    override fun onDialogCancelled() = pending.answer(ClientToolPermissionAnswer.Dismissed)
                },
            )
        shown = ShownDialog(pending, dialog)
        dialog.show()
    }

    private fun allowButtonText(request: ClientToolPermissionRequest): Int = when (request) {
        is ClientToolPermissionRequest.RunToolOnce -> R.string.duckAiClientToolPromptAllowOnce
        is ClientToolPermissionRequest.RunTool, is ClientToolPermissionRequest.GrantAccess -> R.string.duckAiClientToolPromptAllowInChat
    }

    private fun allowAnswer(pending: PendingClientToolPermission): ClientToolPermissionAnswer = when (pending.request) {
        is ClientToolPermissionRequest.RunToolOnce -> ClientToolPermissionAnswer.AllowOnce
        is ClientToolPermissionRequest.RunTool, is ClientToolPermissionRequest.GrantAccess ->
            ClientToolPermissionAnswer.AllowForChat(selections.of(pending)?.removed.orEmpty())
    }

    private fun grantAccessContent(
        activity: Activity,
        pending: PendingClientToolPermission,
    ): View {
        val inflater = LayoutInflater.from(activity)
        val content = ViewClientToolGrantAccessBinding.inflate(inflater)

        fun render() {
            val current = selections.of(pending) ?: return
            content.grantAccessTools.removeAllViews()
            current.remaining.forEach { tool ->
                val toolName = activity.getString(tool.copy.name)
                val row = ItemClientToolGrantAccessBinding.inflate(inflater, content.grantAccessTools, true)
                row.grantAccessToolName.text = toolName
                row.grantAccessToolDescription.text = activity.getString(tool.copy.description)
                if (current.showsBoxes) {
                    row.root.setBackgroundResource(CommonR.drawable.background_rounded_container)
                    val padding = activity.resources.getDimensionPixelSize(CommonR.dimen.keyline_3)
                    row.root.setPaddingRelative(padding, padding, 0, padding)
                }
                if (current.canRemove) {
                    row.grantAccessToolRemove.show()
                    row.grantAccessToolRemove.contentDescription = activity.getString(R.string.duckAiClientToolRemoveContentDescription, toolName)
                    row.grantAccessToolRemove.setOnClickListener {
                        selections.update(pending) { it.remove(tool.name) }
                        render()
                    }
                }
            }
            val removedNames = current.removedTools.map { activity.getString(it.copy.name) }
            if (removedNames.isEmpty()) {
                content.grantAccessRemovedStatus.gone()
            } else {
                content.grantAccessRemovedStatus.show()
                content.grantAccessRemovedText.text = activity.getString(
                    R.string.duckAiClientToolRemovedStatus,
                    GrantAccessSelection.joinNames(
                        removedNames,
                        series = { a, b -> activity.getString(R.string.duckAiClientToolNamesSeries, a, b) },
                        pair = { a, b -> activity.getString(R.string.duckAiClientToolNamesPair, a, b) },
                    ),
                )
            }
        }
        content.grantAccessUndo.setOnClickListener {
            selections.update(pending) { it.undo() }
            render()
        }
        render()
        return content.root
    }

    private class ShownDialog(
        val pending: PendingClientToolPermission,
        val dialog: StackedAlertDialogBuilder,
    )

    companion object {
        // Matched by name because :app hosts the activity and feature modules cannot reference it.
        private const val BROWSER_ACTIVITY = "com.duckduckgo.app.browser.BrowserActivity"

        private const val ALLOW_BUTTON = 0

        /** The pending prompt while its chat's tab is selected in the chat's mode, or null when nothing should be on screen. */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun visiblePrompt(
            pending: Flow<PendingClientToolPermission?>,
            selectedTabId: (BrowserMode) -> Flow<String?>,
        ): Flow<PendingClientToolPermission?> =
            pending
                .flatMapLatest { prompt ->
                    if (prompt == null) {
                        flowOf(null)
                    } else {
                        selectedTabId(prompt.request.browserMode).map { tabId -> prompt.takeIf { tabId == it.request.chatTabId } }
                    }
                }
                .distinctUntilChanged()
    }
}
