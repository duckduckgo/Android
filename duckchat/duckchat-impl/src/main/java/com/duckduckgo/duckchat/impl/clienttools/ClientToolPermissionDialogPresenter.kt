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
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.ui.view.button.ButtonType.SECONDARY
import com.duckduckgo.common.ui.view.dialog.StackedAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.StackedButton
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
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
            .setTitle(pending.request.copy.promptTitle)
            .setMessage(pending.request.copy.promptMessage)
            .setStackedButtons(BUTTONS.map { StackedButton(it.first, SECONDARY) })
            .addEventListener(
                object : StackedAlertDialogBuilder.EventListener() {
                    override fun onButtonClicked(position: Int) = pending.answer(BUTTONS[position].second)

                    override fun onDialogCancelled() = pending.answer(ClientToolPermissionAnswer.DISMISSED)
                },
            )
        shown = ShownDialog(pending, dialog)
        dialog.show()
    }

    private class ShownDialog(
        val pending: PendingClientToolPermission,
        val dialog: StackedAlertDialogBuilder,
    )

    companion object {
        // Matched by name because :app hosts the activity and feature modules cannot reference it.
        private const val BROWSER_ACTIVITY = "com.duckduckgo.app.browser.BrowserActivity"

        private val BUTTONS = listOf(
            R.string.duckAiClientToolPromptAlwaysAllow to ClientToolPermissionAnswer.ALWAYS,
            R.string.duckAiClientToolPromptAllowOnce to ClientToolPermissionAnswer.ALLOW_ONCE,
            R.string.duckAiClientToolPromptNeverAllow to ClientToolPermissionAnswer.NEVER,
        )

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
