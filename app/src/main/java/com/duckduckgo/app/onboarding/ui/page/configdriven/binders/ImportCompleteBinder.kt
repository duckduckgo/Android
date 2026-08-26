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

package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignImportCompleteBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ImportCompleteContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ImportCompleteBinder(
    private val binding: IncludeBrandDesignImportCompleteBinding,
) : StatefulDialogBinder<ContentConfig.ImportComplete, ImportCompleteContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.ImportComplete,
        state: MutableStateFlow<ImportCompleteContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        var rendered = state.value
        importCompleteTitle.setTitle(titleOf(rendered, content).resolve(context))
        apply(rendered, content, context)

        var stateJob: Job? = null

        ContentHandle(
            title = importCompleteTitle,
            fadeTargets = fadeTargets(),
            onContentReady = {
                stateJob = state.onEach { current ->
                    if (current == rendered) return@onEach
                    rendered = current
                    importCompleteTitle.setTitle(titleOf(current, content).resolve(context))

                    importCompleteTitle.snapTitle()
                    scope.animateCardBounds(STATE_CHANGE_DURATION_MS)
                    apply(current, content, context)

                    fadeTargets().forEach { it.alpha = 1f }
                }.launchIn(scope.coroutineScope)
            },
            unbind = { stateJob?.cancel() },
        )
    }

    private fun apply(
        state: ImportCompleteContentState,
        content: ContentConfig.ImportComplete,
        context: Context,
    ) = with(binding) {
        importCompleteShimmer.isVisible = state is ImportCompleteContentState.Parsing
        importCompletePictogram.setImageResource(pictogramOf(state))
        importCompleteImportedRow.isVisible = state is ImportCompleteContentState.Finished
        importCompleteSkippedRow.isVisible = state is ImportCompleteContentState.Finished && state.skipped > 0
        importCompleteBody.isVisible = state !is ImportCompleteContentState.Finished

        when (state) {
            ImportCompleteContentState.Parsing -> importCompleteBody.text = content.parsingBody.resolve(context)
            ImportCompleteContentState.Failed -> importCompleteBody.text = content.failedBody.resolve(context)
            is ImportCompleteContentState.Finished -> {
                importCompleteImportedRow.setPrimaryText(
                    context.getString(R.string.preOnboardingImportCompleteImported, state.imported),
                )
                if (state.skipped > 0) {
                    importCompleteSkippedRow.setPrimaryText(
                        context.getString(R.string.preOnboardingImportCompleteSkipped, state.skipped),
                    )
                }
            }
        }
    }

    private fun pictogramOf(state: ImportCompleteContentState) = when (state) {
        is ImportCompleteContentState.Finished -> R.drawable.ic_success_96
        ImportCompleteContentState.Parsing,
        ImportCompleteContentState.Failed,
        -> R.drawable.ic_passwords_import_96
    }

    private fun titleOf(
        state: ImportCompleteContentState,
        content: ContentConfig.ImportComplete,
    ) = when (state) {
        ImportCompleteContentState.Parsing -> content.parsingTitle
        ImportCompleteContentState.Failed -> content.failedTitle
        is ImportCompleteContentState.Finished -> content.title
    }

    private fun fadeTargets(): List<View> = with(binding) {
        buildList {
            add(importCompletePictogram)
            if (importCompleteBody.isVisible) add(importCompleteBody)
            if (importCompleteShimmer.isVisible) add(importCompleteShimmer)
            if (importCompleteImportedRow.isVisible) add(importCompleteImportedRow)
            if (importCompleteSkippedRow.isVisible) add(importCompleteSkippedRow)
        }
    }

    private companion object {
        const val STATE_CHANGE_DURATION_MS = 300L
    }
}
