/*
 * Copyright (c) 2025 DuckDuckGo
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
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogTitleController
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenPreviewContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.common.ui.view.addBottomShadow
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

/**
 * Stateful. The hairiest port in this set — tab morphing, IME focus, and suggestion-button staggering all
 * live here. Ported from BrandDesignUpdateWelcomePage:
 *  - fresh-entry path :1451-1583 (title typing -> fade-in AnimatorSet -> keyboard focus -> suggestion stagger,
 *    tab listener registration)
 *  - already-visible/snap path :2202-2313 (same shape, no animation)
 *  - mode application: setInputScreenPreviewInputMode :2853-2912 (suggestion binding, submit wiring, hint/
 *    inputType per mode, IME restart on live focus change)
 *  - suggestion stagger: playSuggestionButtonsAnimation :2914-2948
 *
 * State-down-events-up: [InputModeTabLayout]'s tab listener is the only write path into
 * [InputScreenPreviewContentState] — it only records which tab is selected. All rendering (suggestion buttons,
 * hint/inputType, action icon) happens in [applyMode], called once for the initial value and again from the
 * state collector for every value after (including the replayed initial one, same as every other stateful
 * binder in this set). The collector also re-syncs the tab position, guarded by `selectedTabPosition` so it
 * never re-selects a tab that's already showing (loop-safe: legacy's own tab callback distinguishes
 * onTabSelected from onTabReselected the same way, see :1578-1580 / :2295-2297).
 *
 * Documented POC simplifications:
 *  - Legacy wraps every mode switch post-entrance in `TransitionManager.beginDelayedTransition(cardView, ...)`
 *    so the card resizes smoothly between the 1-line search field and 3-line chat field. `cardView` lives on
 *    the shared dialog root, not on this include's binding — per the "binders never touch shared views" rule
 *    this binder has no handle on it at all, so that resize transition is left out; the field still resizes,
 *    it just snaps instead of animating.
 *  - Keyboard show is gated on `screenHeightDp >= 600` like legacy's fresh-entry path (:1516), and deferred
 *    into [ContentHandle.entrance] (after fade) rather than duplicated across a fresh/snap branch — legacy's
 *    snap path skips the keyboard entirely, but showing it uniformly here is harmless and closer to the
 *    animated path's intent. `isFocusable`/`isFocusableInTouchMode` are still set unconditionally at bind time
 *    to cover legacy's snap-path behavior (:2259-2262).
 *  - Uses `ViewCompat.getWindowInsetsController(view)` instead of legacy's `Activity.showKeyboard` extension
 *    (:1519, requires an Activity) — binders only ever see the include's own View tree.
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

        val initialTabIndex = if (state.value.isSearchSelected) 0 else 1
        if (initialTabIndex != 0) {
            inputModeToggle.getTabAt(initialTabIndex)?.select()
        }
        applyMode(content, state.value.isSearchSelected, scope)

        inputModeToggle.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    state.update { it.copy(isSearchSelected = tab.position == 0) }
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            },
        )
        scope.coroutineScope.launch {
            state.collect {
                applyMode(content, it.isSearchSelected, scope)
                val desiredTabIndex = if (it.isSearchSelected) 0 else 1
                if (inputModeToggle.selectedTabPosition != desiredTabIndex) {
                    inputModeToggle.getTabAt(desiredTabIndex)?.select()
                }
            }
        }

        val title = DialogTitleController(inputScreenPreviewTitle, inputScreenPreviewTitleHidden)
        title.set(content.title.resolve(context))

        ContentHandle(
            title = title,
            fadeTargets = listOfNotNull(inputModeToggle.takeIf { content.showModeToggle }, inputModeDemoCard),
            entrance = { afterFade { suggestionButtonsAnimator() } },
        )
    }

    /**
     * Renders everything that depends on which mode is active — suggestion buttons, submit wiring, hint/
     * inputType, action icon — ported from [BrandDesignUpdateWelcomePage.setInputScreenPreviewInputMode]
     * (:2853-2912). Called once for the initial state and again on every subsequent (and replayed-initial)
     * value from the state collector in [bind].
     */
    private fun applyMode(
        content: ContentConfig.InputScreenPreview,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        val suggestions = if (isSearchSelected) content.searchSuggestions else content.chatSuggestions
        bindSuggestionButtons(suggestions, isSearchSelected, scope)

        val submitQuery = {
            val query = inputText.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                scope.emit(NewUserOnboardingEvent.InputDemoQuerySubmitted(query, isChat = !isSearchSelected, fromSuggestion = false))
            }
        }
        inputModeDemoActionIcon.setOnClickListener { submitQuery() }
        inputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitQuery()
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
            inputText.minLines = 3
            inputText.maxLines = 3
            inputText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            inputText.imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED
            inputText.setHint(R.string.preOnboardingInputModeDemoChatHint)
            inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_arrow_right_24)
        }

        // Toggling modes changes inputType/imeOptions while the EditText may already be focused with the
        // keyboard shown; restart input so the IME picks up the new action/Enter behavior immediately.
        if (inputText.hasFocus()) {
            ContextCompat.getSystemService(root.context, InputMethodManager::class.java)?.restartInput(inputText)
        }
    }

    private fun bindSuggestionButtons(
        suggestions: List<DaxDialogIntroOption>,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        listOf(suggestion1, suggestion2, suggestion3).forEachIndexed { index, button ->
            suggestions[index].setOptionView(button)
            button.setOnClickListener {
                scope.emit(
                    NewUserOnboardingEvent.InputDemoQuerySubmitted(
                        query = suggestions[index].link,
                        isChat = !isSearchSelected,
                        fromSuggestion = true,
                    ),
                )
            }
        }
    }

    /**
     * One AnimatorSet the engine owns end-to-end, staggering the three suggestion buttons in — mirrors
     * [BrandDesignUpdateWelcomePage.playSuggestionButtonsAnimation] minus the card-level ChangeBounds (see
     * class doc). The keyboard-focus step that legacy runs at this same point (right after the fade completes,
     * before the stagger starts) happens here too, as plain code rather than an animator listener, since this
     * factory itself is only invoked once fade-in has finished.
     */
    private fun suggestionButtonsAnimator(): Animator = with(binding) {
        if (root.resources.configuration.screenHeightDp >= MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP) {
            root.post { showKeyboard() }
        }

        val buttons = listOf(suggestion1, suggestion2, suggestion3)
        buttons.forEach {
            it.alpha = 0f
            it.isVisible = true
        }

        val buttonAnimators = buttons.mapIndexed { index, button ->
            ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f).apply {
                duration = SUGGESTION_FADE_DURATION
                startDelay = index * SUGGESTION_FADE_DURATION
            }
        }

        AnimatorSet().apply {
            playTogether(buttonAnimators)
            startDelay = SUGGESTIONS_START_DELAY
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    buttons.forEach {
                        it.alpha = 1f
                        it.isVisible = true
                    }
                }
            })
        }
    }

    private fun showKeyboard() = with(binding) {
        inputText.requestFocus()
        ViewCompat.getWindowInsetsController(inputText)?.show(WindowInsetsCompat.Type.ime())
    }

    private companion object {
        const val SUGGESTION_FADE_DURATION = 500L
        const val SUGGESTIONS_START_DELAY = 500L
        const val MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP = 600
    }
}
