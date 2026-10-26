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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.autofill.api.promotion.BookmarksImportScreenPromotionPlugin
import com.duckduckgo.autofill.impl.importing.takeout.webflow.ImportGoogleBookmarksViewModel.Command.Exit
import com.duckduckgo.autofill.impl.importing.takeout.webflow.UserCannotImportReason.Unknown
import com.duckduckgo.autofill.impl.importing.takeout.webflow.journey.ImportGoogleBookmarksJourney
import com.duckduckgo.common.utils.plugins.PluginPoint
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.logcat
import kotlin.time.Duration.Companion.seconds

class ImportGoogleBookmarksViewModel @AssistedInject constructor(
    @Assisted private val launchSource: String,
    @Assisted private val isRedesignEnabled: Boolean,
    private val importJourney: ImportGoogleBookmarksJourney,
    private val promotionPlugins: PluginPoint<BookmarksImportScreenPromotionPlugin>,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState

    private val _commands = Channel<Command>()
    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        importJourney.started(launchSource)
    }

    fun onWaitingForExportStarted() {
        importJourney.startedWaitingForExport()
        updateWebFlowViewMode(ViewMode.WaitingForExport)
    }

    fun onWaitingForExportStopped() {
        importJourney.stoppedWaitingForExport()
        updateWebFlowViewMode(ViewMode.WebFlow)
    }

    private fun updateWebFlowViewMode(viewMode: ViewMode) {
        // The web flow can still report loading changes after it has posted its result.
        // We don't want those to take us off the result screen.
        _viewState.update { current ->
            when (current.viewMode) {
                is ViewMode.WebFlowLoading, is ViewMode.WebFlow, is ViewMode.WaitingForExport -> current.copy(viewMode = viewMode)
                is ViewMode.Success, is ViewMode.Error -> current
            }
        }
    }

    fun onWebFlowFinished(result: ImportGoogleBookmarkResult?) {
        when (result) {
            is ImportGoogleBookmarkResult.Success -> {
                logcat { "Bookmark-import: WebFlow succeeded with ${result.importedCount} bookmarks" }
                importJourney.finishedWithSuccess()
                viewModelScope.launch { showSuccess(result.importedCount) }
            }

            is ImportGoogleBookmarkResult.UserCancelled -> {
                logcat { "Bookmark-import: User cancelled at ${result.stage}" }
                importJourney.cancelled(result.stage)
                viewModelScope.launch { _commands.send(Exit(result)) }
            }

            is ImportGoogleBookmarkResult.Error -> {
                logcat { "Bookmark-import: Import failed with reason: ${result.reason}" }
                importJourney.finishedWithError(error = result.reason)
                _viewState.update { it.copy(viewMode = ViewMode.Error(result.reason)) }
            }

            null -> {
                logcat { "Bookmark-import: Received null result" }
                _viewState.update { it.copy(viewMode = ViewMode.Error(Unknown)) }
            }
        }
    }

    private suspend fun showSuccess(importedCount: Int) {
        val promo = if (isRedesignEnabled) findEligiblePromo(importedCount) else null
        _viewState.update { it.copy(viewMode = ViewMode.Success(importedCount, promo)) }
    }

    private suspend fun findEligiblePromo(importedCount: Int): BookmarksImportScreenPromotionPlugin? {
        val data = BookmarksImportScreenPromotionPlugin.ImportResult(successCount = importedCount)
        // we don't let a broken promo plugin keep the user on the importing screen
        return withTimeoutOrNull(PROMO_TIMEOUT) {
            promotionPlugins.getPlugins().firstOrNull { it.isEligible(data) }
        }
    }

    fun onResultScreenDoneClicked() {
        val result = when (val viewMode = viewState.value.viewMode) {
            is ViewMode.Success -> ImportGoogleBookmarkResult.Success(viewMode.importedCount)
            is ViewMode.Error -> ImportGoogleBookmarkResult.Error(viewMode.reason)
            is ViewMode.WebFlowLoading, is ViewMode.WebFlow, is ViewMode.WaitingForExport -> return
        }
        viewModelScope.launch { _commands.send(Exit(result)) }
    }

    data class ViewState(
        val viewMode: ViewMode = ViewMode.WebFlowLoading,
    )

    sealed interface ViewMode {
        data object WebFlowLoading : ViewMode
        data object WebFlow : ViewMode
        data object WaitingForExport : ViewMode
        data class Success(
            val importedCount: Int,
            val promo: BookmarksImportScreenPromotionPlugin? = null,
        ) : ViewMode
        data class Error(val reason: UserCannotImportReason) : ViewMode
    }

    sealed interface Command {
        data class Exit(val result: ImportGoogleBookmarkResult) : Command
    }

    @AssistedFactory
    interface Factory {
        fun create(
            launchSource: String,
            isRedesignEnabled: Boolean,
        ): ImportGoogleBookmarksViewModel

        class Provider(
            private val assistedFactory: Factory,
            private val launchSource: String,
            private val isRedesignEnabled: Boolean,
        ) : ViewModelProvider.Factory {

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return assistedFactory.create(launchSource, isRedesignEnabled) as T
            }
        }
    }

    companion object {
        val PROMO_TIMEOUT = 1.seconds
    }
}
