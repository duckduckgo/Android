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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignImportCompleteBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaState
import com.duckduckgo.app.onboarding.ui.page.configdriven.ImportCompleteContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.duckduckgo.mobile.android.R as CommonR

class ImportCompleteBinder(
    private val binding: IncludeBrandDesignImportCompleteBinding,
) : StatefulDialogBinder<ContentConfig.ImportComplete, ImportCompleteContentState> {

    override val view: View = binding.root

    private val outcomeVisible = MutableStateFlow(false)

    private var rendered: ImportCompleteContentState? = null
    private var stateJob: Job? = null
    private var transition: Animator? = null

    override fun bind(
        content: ContentConfig.ImportComplete,
        state: MutableStateFlow<ImportCompleteContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        importCompleteImportedRow.resultRowIcon.setImageResource(CommonR.drawable.ic_cross_recolorable_gray_24)
        importCompleteSkippedRow.resultRowIcon.setImageResource(CommonR.drawable.ic_cross_recolorable_gray_24)
        importCompleteFailedRow.resultRowIcon.setImageResource(CommonR.drawable.ic_cross_recolorable_gray_24)

        val initial = state.value
        rendered = initial
        importCompleteTitle.setTitle(titleOf(initial, content).resolve(context))
        apply(initial, content, context)
        outcomeVisible.value = initial !is ImportCompleteContentState.Parsing

        ContentHandle(
            title = importCompleteTitle,
            preTitleFadeTargets = listOf(importCompletePictogram),
            fadeTargets = fadeTargets(),
            onContentReady = {
                stateJob = state
                    .onEach { renderStateChange(it, content, scope) }
                    .launchIn(scope.coroutineScope)
            },
            primaryCtaState = CtaState(
                enabled = outcomeVisible,
                defaultValue = outcomeVisible.value,
            ),
            unbind = {
                stateJob?.cancel()
                transition?.cancel()
            },
        )
    }

    private fun renderStateChange(
        current: ImportCompleteContentState,
        content: ContentConfig.ImportComplete,
        scope: BindScope,
    ) = with(binding) {
        if (current == rendered) return@with
        rendered = current
        val context = root.context

        transition?.cancel()
        val leaving = stateFadeTargets()
        transition = fade(leaving, to = 0f) {
            importCompleteTitle.setTitle(titleOf(current, content).resolve(context))
            importCompleteTitle.snapTitle()

            scope.animateCardBounds(STATE_CHANGE_DURATION_MS)
            apply(current, content, context)
            outcomeVisible.value = current !is ImportCompleteContentState.Parsing

            val arriving = stateFadeTargets()
            arriving.forEach { it.alpha = 0f }

            leaving.filterNot { it in arriving }.forEach { it.alpha = 1f }

            transition = fade(arriving, to = 1f).also { it.start() }
        }.also { it.start() }
    }

    private fun fade(
        views: List<View>,
        to: Float,
        onFaded: () -> Unit = {},
    ): Animator = AnimatorSet().apply {
        duration = STATE_FADE_DURATION_MS
        playTogether(views.map { view -> ObjectAnimator.ofFloat(view, View.ALPHA, to) })
        addListener(
            object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    views.forEach { it.alpha = to }
                    if (!cancelled) onFaded()
                }
            },
        )
    }

    private fun apply(
        state: ImportCompleteContentState,
        content: ContentConfig.ImportComplete,
        context: Context,
    ) = with(binding) {
        importCompleteShimmer.isVisible = state is ImportCompleteContentState.Parsing
        importCompletePictogram.setImageResource(pictogramOf(state))
        importCompleteImportedRow.root.isVisible = state is ImportCompleteContentState.Finished
        importCompleteSkippedRow.root.isVisible = state is ImportCompleteContentState.Finished && state.skipped > 0
        importCompleteFailedRow.root.isVisible = state is ImportCompleteContentState.Failed
        importCompleteResultContainer.isVisible = state !is ImportCompleteContentState.Parsing
        importCompleteBody.isVisible = state is ImportCompleteContentState.Parsing

        when (state) {
            ImportCompleteContentState.Parsing -> importCompleteBody.text = content.parsingBody.resolve(context)
            ImportCompleteContentState.Failed -> importCompleteFailedRow.resultRowText.text = content.failedRow.resolve(context)
            is ImportCompleteContentState.Finished -> {
                importCompleteImportedRow.resultRowText.text =
                    context.getString(R.string.preOnboardingImportCompleteImported, state.imported)
                if (state.skipped > 0) {
                    importCompleteSkippedRow.resultRowText.text =
                        context.getString(R.string.preOnboardingImportCompleteSkipped, state.skipped)
                }
            }
        }
    }

    private fun pictogramOf(state: ImportCompleteContentState) = when (state) {
        is ImportCompleteContentState.Finished -> R.drawable.ic_success_96
        ImportCompleteContentState.Failed -> R.drawable.ic_passwords_alert_96
        ImportCompleteContentState.Parsing -> R.drawable.ic_passwords_import_96
    }

    private fun titleOf(
        state: ImportCompleteContentState,
        content: ContentConfig.ImportComplete,
    ) = when (state) {
        ImportCompleteContentState.Parsing -> content.parsingTitle
        ImportCompleteContentState.Failed -> content.failedTitle
        is ImportCompleteContentState.Finished -> content.title
    }

    private fun stateFadeTargets(): List<View> = fadeTargets() + binding.importCompletePictogram

    private fun fadeTargets(): List<View> = with(binding) {
        buildList {
            if (importCompleteBody.isVisible) add(importCompleteBody)
            if (importCompleteShimmer.isVisible) add(importCompleteShimmer)
            if (importCompleteResultContainer.isVisible) add(importCompleteResultContainer)
        }
    }

    private companion object {
        const val STATE_CHANGE_DURATION_MS = 300L

        const val STATE_FADE_DURATION_MS = 150L
    }
}
