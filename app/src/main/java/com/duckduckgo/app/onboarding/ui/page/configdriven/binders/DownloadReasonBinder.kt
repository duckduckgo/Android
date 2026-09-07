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
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignDialogDownloadReasonBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent.DownloadReasonConfirmed
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaState
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DownloadReasonBinder(
    private val binding: IncludeBrandDesignDialogDownloadReasonBinding,
) : StatefulDialogBinder<ContentConfig.DownloadReason, DownloadReasonContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.DownloadReason,
        state: MutableStateFlow<DownloadReasonContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        downloadReasonBody.text = content.body.resolve(context).preventWidows()
        downloadReasonTitle.setTitle(content.title.resolve(context))

        val icons = listOf(
            downloadReasonOptionSearchIcon,
            downloadReasonOptionAiChatIcon,
            downloadReasonOptionNoAiIcon,
            downloadReasonOptionBlockAdsIcon,
        )
        icons.forEach { icon ->
            // A pictogram's last frame is not always its first, so rewind rather than rest wherever the animation stopped.
            icon.addAnimatorListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        icon.progress = 0f
                    }
                },
            )
        }

        downloadReasonOptionSearch.setOnClickListener {
            applySelection(state, scope, DownloadReasonSelection.SEARCH, downloadReasonOptionSearchIcon)
        }
        downloadReasonOptionAiChat.setOnClickListener {
            applySelection(state, scope, DownloadReasonSelection.AI_CHAT, downloadReasonOptionAiChatIcon)
        }
        downloadReasonOptionNoAi.setOnClickListener {
            applySelection(state, scope, DownloadReasonSelection.NO_AI, downloadReasonOptionNoAiIcon)
        }
        downloadReasonOptionBlockAds.setOnClickListener {
            applySelection(state, scope, DownloadReasonSelection.BLOCK_ADS, downloadReasonOptionBlockAdsIcon)
        }

        state.onEach {
            if (it.selection != null) {
                downloadReasonOptionSearch.applySelected(it.selection == DownloadReasonSelection.SEARCH)
                downloadReasonOptionAiChat.applySelected(it.selection == DownloadReasonSelection.AI_CHAT)
                downloadReasonOptionNoAi.applySelected(it.selection == DownloadReasonSelection.NO_AI)
                downloadReasonOptionBlockAds.applySelected(it.selection == DownloadReasonSelection.BLOCK_ADS)
            }
        }.launchIn(scope.coroutineScope)

        ContentHandle(
            title = downloadReasonTitle,
            fadeTargets = listOf(downloadReasonBody, downloadReasonOptions),
            result = {
                DownloadReasonConfirmed(selection = state.value.selection)
            },
            primaryCtaState = CtaState(enabled = state.map { it.selection != null }, defaultValue = state.value.selection != null),
            unbind = {
                icons.forEach {
                    it.removeAllAnimatorListeners()
                    it.cancelAnimation()
                }
                downloadReasonOptionSearch.setOnClickListener(null)
                downloadReasonOptionAiChat.setOnClickListener(null)
                downloadReasonOptionNoAi.setOnClickListener(null)
                downloadReasonOptionBlockAds.setOnClickListener(null)
            },
        )
    }

    private fun applySelection(
        state: MutableStateFlow<DownloadReasonContentState>,
        scope: BindScope,
        selection: DownloadReasonSelection,
        iconToAnimate: LottieAnimationView,
    ) {
        if (state.value.selection != selection) {
            iconToAnimate.playDelayed(scope)
        }
        state.value = DownloadReasonContentState(selection)
    }

    private fun MaterialCardView.applySelected(selected: Boolean) {
        isSelected = selected
        strokeWidth = resources.getDimensionPixelSize(
            if (selected) R.dimen.brandDesignTabTileStrokeWidthSelected else R.dimen.brandDesignTabTileStrokeWidth,
        )
    }

    private fun LottieAnimationView.playDelayed(scope: BindScope) {
        if (!isAnimating) {
            scope.coroutineScope.launch {
                delay(SELECTION_ANIMATION_DELAY)
                playAnimation()
            }
        }
    }

    companion object {
        private val SELECTION_ANIMATION_DELAY = 300.milliseconds
    }
}
