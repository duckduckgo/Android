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
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignInputScreenPreviewBinding
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenPreviewContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.common.ui.view.addBottomShadow
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The mode tabs are the only write path into the state; everything mode-dependent renders from the collector,
 * so a tab tap and a replayed value take the same path.
 *
 * Legacy wraps each mode switch in a `beginDelayedTransition` on the shared card view so the field resizes
 * smoothly between the one-line search input and the three-line chat input. A binder has no handle on the card,
 * so the field snaps to its new size instead.
 */
class InputScreenPreviewBinder(
    private val binding: IncludeBrandDesignInputScreenPreviewBinding,
) : StatefulDialogBinder<ContentConfig.InputScreenPreview, InputScreenPreviewContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.InputScreenPreview,
        state: MutableStateFlow<InputScreenPreviewContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        if (Build.VERSION.SDK_INT >= 28) {
            inputModeDemoCard.addBottomShadow()
        }

        inputText.isFocusable = true
        inputText.isFocusableInTouchMode = true

        inputModeToggle.isVisible = content.showModeToggle
        applyMode(content, state.value.isSearchSelected, scope)

        val tabListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                state.update { it.copy(isSearchSelected = tab.position == SEARCH_TAB_INDEX) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        }
        inputModeToggle.addOnTabSelectedListener(tabListener)
        scope.coroutineScope.launch {
            state.collect {
                applyMode(content, it.isSearchSelected, scope)
                val target = if (it.isSearchSelected) SEARCH_TAB_INDEX else CHAT_TAB_INDEX
                if (inputModeToggle.selectedTabPosition != target) {
                    inputModeToggle.getTabAt(target)?.select()
                }
            }
        }

        inputScreenPreviewTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = inputScreenPreviewTitle,
            fadeTargets = listOfNotNull(inputModeToggle.takeIf { content.showModeToggle }, inputModeDemoCard),
            afterFade = { suggestionButtonsAnimator() },
            onContentReady = { showKeyboardIfRoom() },
            unbind = { inputModeToggle.removeOnTabSelectedListener(tabListener) },
        )
    }

    private fun applyMode(
        content: ContentConfig.InputScreenPreview,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        bindSuggestionButtons(
            suggestions = if (isSearchSelected) content.searchSuggestions else content.chatSuggestions,
            isSearchSelected = isSearchSelected,
            scope = scope,
        )

        val submitTypedQuery = {
            val query = inputText.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                scope.execute(ContentInteraction.SubmitInput(query, isChat = !isSearchSelected, fromSuggestion = false))
            }
        }
        inputModeDemoActionIcon.setOnClickListener { submitTypedQuery() }
        inputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitTypedQuery()
                true
            } else {
                false
            }
        }

        if (isSearchSelected) {
            inputText.minLines = 1
            inputText.maxLines = 1
            inputText.inputType = InputType.TYPE_CLASS_TEXT
            inputText.imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputText.setHint(R.string.preOnboardingInputModeDemoSearchHint)
            inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_find_search_24)
        } else {
            inputText.minLines = CHAT_INPUT_LINES
            inputText.maxLines = CHAT_INPUT_LINES
            inputText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            inputText.imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED
            inputText.setHint(R.string.preOnboardingInputModeDemoChatHint)
            inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_arrow_right_24)
        }

        // A mode switch can land while the field is already focused, so the IME has to be told to pick up the
        // new action and Enter behaviour.
        if (inputText.hasFocus()) {
            ContextCompat.getSystemService(root.context, InputMethodManager::class.java)?.restartInput(inputText)
        }
    }

    private fun bindSuggestionButtons(
        suggestions: List<DaxDialogIntroOption>,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        suggestionButtons().forEachIndexed { index, button ->
            suggestions[index].setOptionView(button)
            button.setOnClickListener {
                scope.execute(
                    ContentInteraction.SubmitInput(
                        query = suggestions[index].link,
                        isChat = !isSearchSelected,
                        fromSuggestion = true,
                    ),
                )
            }
        }
    }

    private fun suggestionButtons() = listOf(binding.suggestion1, binding.suggestion2, binding.suggestion3)

    /**
     * Staggers the suggestion buttons in. They are only tappable once the stagger completes: the card stops
     * intercepting touches as the entrance starts, so a button revealed here would otherwise be tappable while
     * still invisible.
     */
    private fun suggestionButtonsAnimator(): Animator {
        val buttons = suggestionButtons()
        buttons.forEach {
            it.alpha = 0f
            it.isClickable = false
            it.isVisible = true
        }

        val fades = buttons.mapIndexed { index, button ->
            ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f).apply {
                duration = SUGGESTION_FADE_DURATION_MS
                startDelay = index * SUGGESTION_FADE_DURATION_MS
            }
        }

        return AnimatorSet().apply {
            playTogether(fades)
            startDelay = SUGGESTIONS_START_DELAY_MS
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        buttons.forEach {
                            it.alpha = 1f
                            it.isClickable = true
                            it.isVisible = true
                        }
                    }
                },
            )
        }
    }

    private fun showKeyboardIfRoom() {
        if (binding.root.resources.configuration.screenHeightDp < MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP) return
        with(binding) {
            root.post {
                if (!root.isAttachedToWindow) return@post
                inputText.requestFocus()
                ViewCompat.getWindowInsetsController(inputText)?.show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private companion object {
        const val SEARCH_TAB_INDEX = 0
        const val CHAT_TAB_INDEX = 1
        const val CHAT_INPUT_LINES = 3
        const val SUGGESTION_FADE_DURATION_MS = 500L
        const val SUGGESTIONS_START_DELAY_MS = 500L
        const val MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP = 600
    }
}
