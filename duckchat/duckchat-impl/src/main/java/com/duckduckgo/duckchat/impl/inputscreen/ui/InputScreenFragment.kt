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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.common.utils.keyboardVisibilityFlow
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultCodes
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenBrowserButtonsConfig
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentInputScreenBinding
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.AnimateLogoToProgress
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.EditWithSelectedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.HideKeyboard
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SetInputModeWidgetScrollPosition
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SetLogoProgress
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.ShowKeyboard
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitSearch
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchToTab
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.UserSubmittedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.InputFieldCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.InputScreenVisibilityState
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEARCH
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEND
import com.duckduckgo.duckchat.impl.inputscreen.ui.tabs.InputScreenPagerAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputModeWidget
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputScreenButtons
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.SwipeableRecyclerView
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel.InputScreenViewModelFactory
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel.InputScreenViewModelProviderFactory
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.SearchCancelled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceRecognitionSuccess
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceSearchDisabled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import com.duckduckgo.voice.api.VoiceSearchLauncher.VoiceSearchMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class InputScreenFragment : DuckDuckGoFragment(R.layout.fragment_input_screen) {
    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var voiceSearchAvailability: VoiceSearchAvailability

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: InputScreenViewModelFactory

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var inputScreenConfigResolver: InputScreenConfigResolver

    @Inject
    lateinit var duckChatFeature: DuckChatFeature

    private val viewModel: InputScreenViewModel by lazy {
        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        val currentOmnibarText = params?.query ?: ""
        val providerFactory = InputScreenViewModelProviderFactory(viewModelFactory, currentOmnibarText = currentOmnibarText)
        ViewModelProvider(owner = this, factory = providerFactory)[InputScreenViewModel::class.java]
    }

    private val binding: FragmentInputScreenBinding by viewBinding()

    private lateinit var inputModeWidget: InputModeWidget
    private lateinit var contentSeparator: View
    private lateinit var inputScreenButtons: InputScreenButtons

    private var isKeyboardCurrentlyVisible: Boolean = false
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var previousSearchMode: Boolean? = null
    private var wasAutoCompleteVisibleOnSwipeStart: Boolean = false
    private var hadInputTextOnSwipeStart: Boolean = false
    private var autoCompleteTargetVisibility: Boolean = false
    private var chatSuggestionsTargetVisibility: Boolean = false

    private val pageChangeCallback =
        object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.onPageSelected(position)
                inputModeWidget.selectTab(position)
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                viewModel.onPageScrolled(position, positionOffset, wasAutoCompleteVisibleOnSwipeStart, hadInputTextOnSwipeStart)
            }

            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        wasAutoCompleteVisibleOnSwipeStart = autoCompleteTargetVisibility
                        hadInputTextOnSwipeStart = inputModeWidget.text.isNotBlank()
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        viewModel.onScrollStateIdle()
                        wasAutoCompleteVisibleOnSwipeStart = false
                        hadInputTextOnSwipeStart = false

                        if (autoCompleteTargetVisibility) {
                            binding.viewPager.isUserInputEnabled = false
                        }
                    }
                    else -> {}
                }
            }
        }

    private lateinit var pagerAdapter: InputScreenPagerAdapter
    private var logoAnimator: ValueAnimator? = null

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        globalLayoutListener =
            ViewTreeObserver.OnGlobalLayoutListener {
                val r = Rect()
                binding.root.getWindowVisibleDisplayFrame(r)
                val screenHeight = binding.root.rootView.height
                val keypadHeight = screenHeight - r.bottom

                val previouslyVisible = isKeyboardCurrentlyVisible
                isKeyboardCurrentlyVisible = keypadHeight > screenHeight * 0.15 // 15% of screen height

                if (previouslyVisible != isKeyboardCurrentlyVisible) {
                    if (isKeyboardCurrentlyVisible) {
                        logcat { "inputScreenLauncher: Keyboard shown (GlobalLayout)" }
                    } else {
                        logcat { "inputScreenLauncher: Keyboard hidden (GlobalLayout)" }
                        inputModeWidget.clearInputFocus()
                    }
                }
            }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        inputModeWidget =
            InputModeWidget(requireContext()).also {
                it.id = R.id.inputModeWidget
            }

        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        val initialText = params?.query ?: ""
        val showMainButtons = inputScreenConfigResolver.mainButtonsEnabled()
        inputModeWidget.provideInitialInputState(initialText, showMainButtons)

        val useTopBar = inputScreenConfigResolver.useTopBar()
        val separatorHeightPx = resources.getDimensionPixelSize(R.dimen.inputScreenContentSeparatorHeight)
        contentSeparator =
            View(context).apply {
                val typedValue = TypedValue()
                val attributeResId =
                    if (useTopBar) {
                        CommonR.attr.daxColorShade
                    } else {
                        CommonR.attr.daxColorShadeSolid
                    }
                requireContext().theme.resolveAttribute(attributeResId, typedValue, true)
                setBackgroundColor(typedValue.data)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        separatorHeightPx,
                        if (useTopBar) Gravity.BOTTOM else Gravity.TOP,
                    )
            }

        inputScreenButtons = InputScreenButtons(requireContext(), useTopBar = useTopBar)

        if (useTopBar) {
            binding.inputModeWidgetContainerTop.addView(inputModeWidget)
            binding.inputModeWidgetContainerTop.addView(contentSeparator)
            binding.inputScreenButtonsContainer.addView(inputScreenButtons)
        } else {
            inputModeWidget.setInputScreenBottomButtons(inputScreenButtons)
            binding.inputModeWidgetContainerBottom.addView(inputModeWidget)
            binding.inputModeWidgetContainerBottom.addView(contentSeparator)
        }

        configureViewPager()

        binding.newTabContainerScrollView.setViewPager(binding.viewPager)

        if (!useTopBar) {
            binding.autoCompleteBottomFadeContainer.isVisible = false
            binding.chatSuggestionsBottomFadeContainer.isVisible = false
            binding.ddgLogoContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin -= resources.getDimensionPixelSize(R.dimen.inputScreenLogoBottomBarTopMargin)
            }
        }

        val tabs = when (val browserButtonsConfig = params?.browserButtonsConfig) {
            is InputScreenBrowserButtonsConfig.Enabled -> browserButtonsConfig.tabs
            else -> 0
        }
        configureOmnibar(tabs, useTopBar)

        configureVoice(useTopBar)
        configureObservers()
        configureLogoAnimation()
        configureKeyboardListener()

        val launchOnChat = params?.launchOnChat ?: false
        if (launchOnChat) {
            inputModeWidget.initOnChat()
        } else {
            inputModeWidget.initOnSearch()
        }
        updateMenuIconButton(params?.useBottomSheetMenu ?: false)

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = inputModeWidget.text
                    val data = Intent().putExtra(InputScreenActivityResultParams.CANCELED_DRAFT_PARAM, query)
                    requireActivity().setResult(Activity.RESULT_CANCELED, data)
                    exitInputScreen()
                }
            },
        )

        configureInputScreenButtons()
        inputModeWidget.inputField.post {
            showKeyboard(inputModeWidget.inputField)
        }

        viewModel.fireShownPixel()
    }

    override fun onDestroyView() {
        logoAnimator?.cancel()
        logoAnimator = null
        binding.ddgLogo.clearAnimation()
        binding.ddgLogoContainer.animate().cancel()
        binding.autoCompleteOverlay.animate().cancel()
        binding.chatSuggestionsOverlay.animate().cancel()
        binding.newTabContainerScrollView.animate().cancel()
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        globalLayoutListener?.let {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        globalLayoutListener = null
        super.onDestroyView()
    }

    private fun configureObservers() {
        viewModel.command.observe(
            viewLifecycleOwner,
        ) {
            processCommand(it)
        }

        viewModel.inputFieldCommand
            .onEach { command ->
                when (command) {
                    is InputFieldCommand.SelectAll -> {
                        inputModeWidget.selectAllText()
                    }
                }
            }.launchIn(lifecycleScope)

        viewModel.submitButtonIconState
            .onEach { iconState ->
                val iconResource =
                    when (iconState.icon) {
                        SEARCH -> com.duckduckgo.mobile.android.R.drawable.ic_find_search_24
                        SEND -> com.duckduckgo.mobile.android.R.drawable.ic_arrow_right_24
                    }
                inputScreenButtons.setSendButtonIcon(iconResource)
            }.launchIn(lifecycleScope)

        viewModel.inputFieldState
            .onEach { inputBoxState ->
                inputModeWidget.canExpand = inputBoxState.canExpand
            }.launchIn(lifecycleScope)

        viewModel.visibilityState
            .onEach { state ->
                updateLogoVisibility(state)
                beginRootTransition()
                updateFavoritesVisibility(state.searchMode, !state.autoCompleteSuggestionsVisible)
                if (duckChatFeature.aiChatSuggestions().isEnabled()) {
                    updateOverlaysForModeChange(state)
                } else {
                    hideAutoCompleteIfOnChatTab(state)
                }
                previousSearchMode = state.searchMode
                updateButtonVisibility(state)
                inputScreenButtons.setNewLineButtonVisible(state.newLineButtonVisible)
            }.launchIn(lifecycleScope)

        viewModel.visibilityState
            .map { it.actionButtonsContainerVisible }
            .distinctUntilChanged()
            .onEach {
                inputModeWidget.setInputScreenButtonsVisible(inputScreenConfigResolver.useTopBar() && it)
            }.launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is UserSubmittedQuery -> inputModeWidget.submitMessage(command.query)
            is EditWithSelectedQuery -> inputModeWidget.text = command.query
            is SwitchToTab -> {
                val data = Intent().putExtra(InputScreenActivityResultParams.TAB_ID_PARAM, command.tabId)
                requireActivity().setResult(InputScreenActivityResultCodes.SWITCH_TO_TAB_REQUESTED, data)
                exitInputScreen()
            }

            is SubmitSearch -> submitSearchQuery(command.query)
            is SubmitChat -> submitChatQuery(command.query)
            is ShowKeyboard -> showKeyboard(inputModeWidget.inputField)
            is HideKeyboard -> hideKeyboard(inputModeWidget.inputField)
            is SetInputModeWidgetScrollPosition -> inputModeWidget.setScrollPosition(command.position, command.offset)
            is SetLogoProgress -> setLogoProgress(command.targetProgress)
            is AnimateLogoToProgress -> animateLogoToProgress(command.targetProgress)
            is Command.FireButtonRequested -> {
                requireActivity().setResult(InputScreenActivityResultCodes.FIRE_BUTTON_REQUESTED)
                exitInputScreen()
            }

            is Command.MenuRequested -> {
                requireActivity().setResult(InputScreenActivityResultCodes.MENU_REQUESTED)
                exitInputScreen()
            }

            is Command.TabSwitcherRequested -> {
                requireActivity().setResult(InputScreenActivityResultCodes.TAB_SWITCHER_REQUESTED)
                exitInputScreen()
            }

            is Command.LaunchDeviceApplication -> {
                try {
                    startActivity(command.deviceAppSuggestion.launchIntent)
                    // This command is only available when launched from widgets or system search,
                    // and since we're moving to a new task (in another app),
                    // ensure that our task is finished so that it gets removed from recents.
                    requireActivity().finishAffinity()
                } catch (e: Exception) {
                    viewModel.appNotFound(command.deviceAppSuggestion)
                }
            }

            is Command.ShowAppNotFoundMessage -> {
                Toast.makeText(requireContext(), R.string.autocompleteDeviceAppNotFound, LENGTH_SHORT).show()
            }
        }
    }

    private fun configureViewPager() {
        pagerAdapter = InputScreenPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun configureOmnibar(tabs: Int, useTopBar: Boolean) =
        with(inputModeWidget) {
            onSearchSent = { query ->
                viewModel.onSearchSubmitted(query)
            }
            onChatSent = { query ->
                viewModel.onChatSubmitted(query)
            }
            onBack = {
                requireActivity().onBackPressed()
            }
            onSearchSelected = {
                binding.viewPager.setCurrentItem(0, true)
                viewModel.onSearchSelected()
                viewModel.onSearchInputTextChanged(inputModeWidget.text)

                if (!duckChatFeature.aiChatSuggestions().isEnabled()) {
                    if (viewModel.visibilityState.value.showSearchLogo && !viewModel.visibilityState.value.autoCompleteSuggestionsVisible) {
                        binding.ddgLogoContainer.isVisible = true
                    } else {
                        if (binding.ddgLogoContainer.isVisible) {
                            binding.ddgLogoContainer.animate()
                                .alpha(0f)
                                .setDuration(LOGO_FADE_DURATION)
                                .withEndAction {
                                    binding.ddgLogoContainer.isVisible = false
                                    binding.ddgLogoContainer.alpha = 1f
                                }
                                .start()
                        }
                    }
                }
            }
            onChatSelected = {
                binding.viewPager.setCurrentItem(1, true)
                viewModel.onChatSelected()
                viewModel.onChatInputTextChanged(inputModeWidget.text)
                if (!useTopBar) {
                    inputScreenButtons.setSendButtonVisible(true)
                    inputModeWidget.setInputScreenButtonsVisible(true)
                }
                if (duckChatFeature.aiChatSuggestions().isEnabled()) {
                    if (binding.ddgLogoContainer.isVisible) {
                        fadeOutLogo()
                    }
                } else if (viewModel.visibilityState.value.showSearchLogo && !viewModel.visibilityState.value.autoCompleteSuggestionsVisible) {
                    binding.ddgLogoContainer.isVisible = true
                } else if (viewModel.visibilityState.value.showChatLogo) {
                    binding.ddgLogo.progress = 1f
                    binding.ddgLogoContainer.alpha = 0f
                    binding.ddgLogoContainer.isVisible = true
                    binding.ddgLogoContainer.animate().alpha(1f).setDuration(LOGO_FADE_DURATION).start()
                }
            }
            onSubmitMessageAvailable = { isAvailable ->
                viewModel.onSubmitMessageAvailableChange(isAvailable)
            }
            onVoiceInputAllowed = { isAllowed ->
                viewModel.onVoiceInputAllowedChange(isAllowed)
            }
            onSearchTextChanged = { text ->
                viewModel.onSearchInputTextChanged(text)
            }
            onChatTextChanged = { text ->
                viewModel.onChatInputTextChanged(text)
                if (!useTopBar) {
                    val isOnChatTab = inputModeWidget.isChatTabSelected() || !viewModel.visibilityState.value.searchMode
                    inputScreenButtons.setSendButtonVisible(isOnChatTab)
                    inputModeWidget.setInputScreenButtonsVisible(isOnChatTab)
                }
            }
            onInputFieldClicked = {
                viewModel.onInputFieldTouched()
            }
            onTabTapped = { index ->
                viewModel.onTabTapped(index, inputModeWidget.text)
            }
            tabSwitcherButton.count = tabs
            onTabSwitcherTapped = {
                viewModel.onTabSwitcherTapped()
            }
            onFireButtonTapped = {
                viewModel.onFireButtonTapped()
            }
            onMenuTapped = {
                viewModel.onBrowserMenuTapped()
            }
            onVoiceClick = {
                voiceSearchLauncher.launch(requireActivity(), VoiceSearchMode.fromValue(inputModeWidget.getSelectedTabPosition()))
            }
            onClearTextTapped = {
                viewModel.onClearTextTapped()
            }
        }

    private fun submitChatQuery(query: String) {
        val data = Intent().putExtra(InputScreenActivityResultParams.CANCELED_DRAFT_PARAM, query)
        requireActivity().setResult(Activity.RESULT_CANCELED, data)
        exitInputScreen()
    }

    private fun submitSearchQuery(query: String) {
        val data = Intent().putExtra(InputScreenActivityResultParams.SEARCH_QUERY_PARAM, query)
        requireActivity().setResult(InputScreenActivityResultCodes.NEW_SEARCH_REQUESTED, data)
        exitInputScreen()
    }

    private fun configureVoice(useTopBar: Boolean) {
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) {
            when (it) {
                is VoiceRecognitionSuccess -> {
                    when (val result = it.result) {
                        is VoiceSearchLauncher.VoiceRecognitionResult.SearchResult -> {
                            viewModel.onSearchSubmitted(result.query)
                        }
                        is VoiceSearchLauncher.VoiceRecognitionResult.DuckAiResult -> {
                            viewModel.onChatSubmitted(result.query)
                        }
                    }
                }

                is SearchCancelled -> {}
                is VoiceSearchDisabled -> {
                    viewModel.onVoiceSearchDisabled()
                }
            }
        }
        viewModel.visibilityState
            .onEach {
                if (useTopBar) {
                    inputScreenButtons.setVoiceButtonVisible(it.voiceInputButtonVisible)
                } else {
                    val inputText = inputModeWidget.text
                    if (inputText.isEmpty()) {
                        inputModeWidget.setVoiceButtonVisible(it.voiceInputButtonVisible)
                        inputScreenButtons.setVoiceButtonVisible(false)
                    } else {
                        inputScreenButtons.setVoiceButtonVisible(it.voiceInputButtonVisible)
                        inputModeWidget.setVoiceButtonVisible(false)
                    }
                }
            }.launchIn(lifecycleScope)
        if (inputScreenConfigResolver.shouldLaunchVoiceSearch()) {
            voiceSearchLauncher.launch(requireActivity())
        }
    }

    private fun configureInputScreenButtons() {
        inputScreenButtons.onSendClick = {
            // todo remove round-tripping through the input mode widget - actions should go directly to the view model
            inputModeWidget.submitMessage()
            viewModel.onSendButtonClicked()
        }
        inputScreenButtons.onNewLineClick = {
            inputModeWidget.printNewLine()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED)
        }
        inputScreenButtons.onVoiceClick = {
            voiceSearchLauncher.launch(requireActivity(), VoiceSearchMode.fromValue(inputModeWidget.getSelectedTabPosition()))
        }
    }

    private fun configureLogoAnimation() =
        with(binding.ddgLogo) {
            setMinAndMaxFrame(0, LOGO_MAX_FRAME)
            setAnimation(
                if (appTheme.isLightModeEnabled()) {
                    R.raw.duckduckgo_ai_transition_light
                } else {
                    R.raw.duckduckgo_ai_transition_dark
                },
            )
            val initialProgress = if (viewModel.visibilityState.value.searchMode) 0f else 1f
            progress = initialProgress
        }

    private fun setLogoProgress(targetProgress: Float) {
        if (duckChatFeature.aiChatSuggestions().isEnabled()) return
        binding.ddgLogo.progress = targetProgress
    }

    private fun animateLogoToProgress(targetProgress: Float) {
        logoAnimator?.cancel()
        if (duckChatFeature.aiChatSuggestions().isEnabled()) {
            binding.ddgLogo.progress = 0f
            return
        }
        binding.ddgLogo.apply {
            logoAnimator =
                ValueAnimator.ofFloat(progress, targetProgress).apply {
                    duration = LOGO_ANIMATION_DURATION
                    addUpdateListener { progress = it.animatedValue as Float }
                    start()
                }
        }
    }

    private fun exitInputScreen() {
        requireActivity().finish()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume()
    }

    private fun configureKeyboardListener() {
        binding.root.rootView.keyboardVisibilityFlow()
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .distinctUntilChanged()
            .onEach { isVisible ->
                if (isVisible) {
                    viewModel.sendKeyboardFocusedPixel()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun updateLogoVisibility(state: InputScreenVisibilityState) {
        val wasChatMode = previousSearchMode == false
        val logoWasVisible = binding.ddgLogoContainer.isVisible && binding.ddgLogoContainer.alpha > 0f

        val shouldBeVisible = when {
            state.searchMode -> state.showSearchLogo
            duckChatFeature.aiChatSuggestions().isEnabled() -> false
            else -> state.showChatLogo
        }

        if (duckChatFeature.aiChatSuggestions().isEnabled() && shouldBeVisible && !logoWasVisible && wasChatMode && state.searchMode) {
            fadeInLogo()
        } else if (!shouldBeVisible && logoWasVisible && wasChatMode && state.searchMode) {
            fadeOutLogo()
        } else {
            showOrHideLogo(shouldBeVisible, !logoWasVisible, state.searchMode)
        }
    }

    private fun fadeInLogo() {
        logoAnimator?.cancel()
        binding.ddgLogoContainer.animate().cancel()
        binding.ddgLogo.progress = 0f
        binding.ddgLogoContainer.alpha = 0f
        binding.ddgLogoContainer.isVisible = true
        binding.ddgLogoContainer.animate()
            .alpha(1f)
            .setDuration(LOGO_FADE_DURATION)
            .start()
    }

    private fun fadeOutLogo() {
        binding.ddgLogoContainer.animate()
            .alpha(0f)
            .setDuration(LOGO_FADE_DURATION)
            .withEndAction {
                binding.ddgLogoContainer.isVisible = false
                binding.ddgLogoContainer.alpha = 1f
            }
            .start()
    }

    private fun showOrHideLogo(shouldBeVisible: Boolean, wasHidden: Boolean, searchMode: Boolean) {
        binding.ddgLogoContainer.animate().cancel()
        binding.ddgLogoContainer.alpha = 1f
        if (shouldBeVisible && wasHidden) {
            binding.ddgLogo.progress = if (searchMode || duckChatFeature.aiChatSuggestions().isEnabled()) 0f else 1f
        }
        binding.ddgLogoContainer.isVisible = shouldBeVisible
    }

    private fun hideAutoCompleteIfOnChatTab(state: InputScreenVisibilityState) {
        if (!state.searchMode && autoCompleteTargetVisibility) {
            autoCompleteTargetVisibility = false
            binding.autoCompleteOverlay.animate().cancel()
            hideOverlay(binding.autoCompleteOverlay, ::invalidateAutoCompleteBlurView)
        }
    }

    private fun updateOverlaysForModeChange(state: InputScreenVisibilityState) {
        if (state.searchMode) {
            if (chatSuggestionsTargetVisibility) {
                chatSuggestionsTargetVisibility = false
                hideOverlayImmediately(binding.chatSuggestionsOverlay, ::invalidateChatSuggestionsBlurView)
            }
        } else {
            if (autoCompleteTargetVisibility) {
                autoCompleteTargetVisibility = false
                hideOverlayImmediately(binding.autoCompleteOverlay, ::invalidateAutoCompleteBlurView)
            }
            updateChatSuggestionsVisibility(viewModel.chatSuggestions.value.isNotEmpty())
        }
    }

    private fun updateMenuIconButton(useBottomSheetMenu: Boolean) {
        val drawable = if (useBottomSheetMenu) {
            com.duckduckgo.mobile.android.R.drawable.ic_menu_hamburger_24
        } else {
            com.duckduckgo.mobile.android.R.drawable.ic_menu_vertical_24
        }
        inputModeWidget.setMenuIcon(drawable)
    }

    private fun updateButtonVisibility(state: InputScreenVisibilityState) {
        val useTopBar = inputScreenConfigResolver.useTopBar()
        if (useTopBar) {
            inputScreenButtons.setSendButtonVisible(state.submitButtonVisible)
        } else {
            val isOnChatTab = inputModeWidget.isChatTabSelected() || !state.searchMode
            inputScreenButtons.setSendButtonVisible(isOnChatTab)
            inputModeWidget.setInputScreenButtonsVisible(isOnChatTab)
        }
        inputModeWidget.setMainButtonsVisible(state.mainButtonsVisible)
    }

    fun getFavoritesContainer(): FrameLayout = binding.newTabContainerLayout

    fun getAutoCompleteRecyclerView(): SwipeableRecyclerView {
        return binding.autoCompleteSuggestionsList
    }

    fun getAutoCompleteBottomFadeContainer(): FrameLayout {
        return binding.autoCompleteBottomFadeContainer
    }

    fun getChatSuggestionsRecyclerView(): SwipeableRecyclerView {
        return binding.chatSuggestionsRecyclerView
    }

    fun getChatSuggestionsBottomFadeContainer(): FrameLayout {
        return binding.chatSuggestionsBottomFadeContainer
    }

    fun getViewPager(): ViewPager2 {
        return binding.viewPager
    }

    fun updateAutoCompleteVisibility(visible: Boolean) {
        val shouldShow = visible && viewModel.visibilityState.value.searchMode
        if (autoCompleteTargetVisibility == shouldShow) return

        autoCompleteTargetVisibility = shouldShow
        binding.autoCompleteOverlay.animate().cancel()
        beginRootTransition()
        if (shouldShow) {
            showOverlay(binding.autoCompleteOverlay, ::invalidateAutoCompleteBlurView)
        } else {
            hideOverlay(binding.autoCompleteOverlay, ::invalidateAutoCompleteBlurView)
        }
    }

    fun updateChatSuggestionsVisibility(visible: Boolean) {
        val shouldShow = visible && !viewModel.visibilityState.value.searchMode
        if (chatSuggestionsTargetVisibility == shouldShow) return

        chatSuggestionsTargetVisibility = shouldShow
        binding.chatSuggestionsOverlay.animate().cancel()
        beginRootTransition()
        if (shouldShow) {
            showOverlay(binding.chatSuggestionsOverlay, ::invalidateChatSuggestionsBlurView)
        } else {
            hideOverlay(binding.chatSuggestionsOverlay, ::invalidateChatSuggestionsBlurView)
        }
    }

    private fun showOverlay(overlay: View, onAnimationUpdate: () -> Unit = {}) {
        disableViewPagerInput()
        overlay.elevation = 3f.toPx()
        overlay.alpha = 0f
        overlay.isVisible = true
        overlay.bringToFront()
        overlay.animate()
            .alpha(1f)
            .setDuration(OVERLAY_ANIMATION_DURATION)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setUpdateListener { onAnimationUpdate() }
            .start()
    }

    private fun hideOverlay(overlay: View, onAnimationUpdate: () -> Unit = {}) {
        overlay.animate()
            .alpha(0f)
            .setDuration(OVERLAY_ANIMATION_DURATION)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setUpdateListener { onAnimationUpdate() }
            .withEndAction {
                overlay.isVisible = false
                overlay.alpha = 1f
                overlay.elevation = 0f
                if (duckChatFeature.aiChatSuggestions().isEnabled()) {
                    enableViewPagerInputIfNoOverlays()
                } else {
                    enableViewPagerInputIfNoFavorites()
                }
            }
            .start()
    }

    private fun hideOverlayImmediately(overlay: View, onAnimationUpdate: () -> Unit = {}) {
        overlay.animate().cancel()
        overlay.visibility = View.INVISIBLE
        hideOverlay(overlay, onAnimationUpdate)
    }

    private fun invalidateAutoCompleteBlurView() {
        binding.autoCompleteBottomFadeContainer.getChildAt(0)?.invalidate()
    }

    private fun invalidateChatSuggestionsBlurView() {
        binding.chatSuggestionsBottomFadeContainer.getChildAt(0)?.invalidate()
    }

    fun onFavoritesContentChanged(hasContent: Boolean) {
        val state = viewModel.visibilityState.value
        updateFavoritesVisibility(
            searchMode = state.searchMode,
            autocompleteHidden = !state.autoCompleteSuggestionsVisible,
            hasContent = hasContent,
        )
        showLogoIfNoContent(hasContent, state)
    }

    private fun showLogoIfNoContent(hasContent: Boolean, state: InputScreenVisibilityState) {
        if (duckChatFeature.aiChatSuggestions().isEnabled() && !state.searchMode) return
        if (!hasContent && !state.autoCompleteSuggestionsVisible) {
            binding.ddgLogoContainer.isVisible = true
            binding.ddgLogo.progress = if (state.searchMode) 0f else 1f
        }
    }

    private fun updateFavoritesVisibility(
        searchMode: Boolean,
        autocompleteHidden: Boolean,
        hasContent: Boolean? = null,
    ) {
        val actualHasContent = hasContent ?: binding.newTabContainerLayout.isNotEmpty()
        val shouldShow = searchMode && autocompleteHidden && actualHasContent
        val isCurrentlyVisible = binding.newTabContainerScrollView.isVisible

        when {
            !actualHasContent -> hideFavoritesImmediate()
            isCurrentlyVisible == shouldShow -> return
            !autocompleteHidden -> hideFavoritesForAutocomplete()
            else -> {
                prepareFavoritesForAnimation(shouldShow)
                animateFavoritesVisibility(shouldShow)
            }
        }
    }

    private fun hideFavoritesImmediate() {
        binding.newTabContainerScrollView.isVisible = false
        binding.newTabContainerScrollView.elevation = 0f
        enableViewPagerInput()
    }

    private fun hideFavoritesForAutocomplete() {
        val scrollView = binding.newTabContainerScrollView
        scrollView.isVisible = false
        scrollView.alpha = 1f
        scrollView.translationY = 0f
        scrollView.elevation = 0f
    }

    private fun prepareFavoritesForAnimation(show: Boolean) {
        val scrollView = binding.newTabContainerScrollView
        if (show) {
            scrollView.elevation = 2f.toPx()
            disableViewPagerInput()
        } else {
            scrollView.elevation = 0f
            enableViewPagerInput()
        }
    }

    private fun enableViewPagerInput() {
        binding.viewPager.isUserInputEnabled = true
        binding.viewPager.isClickable = true
        binding.viewPager.isFocusable = true
    }

    private fun disableViewPagerInput() {
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.isClickable = false
        binding.viewPager.isFocusable = false
    }

    private fun enableViewPagerInputIfNoOverlays() {
        if (!binding.newTabContainerScrollView.isVisible &&
            !autoCompleteTargetVisibility &&
            !chatSuggestionsTargetVisibility
        ) {
            enableViewPagerInput()
        }
    }

    private fun enableViewPagerInputIfNoFavorites() {
        if (!binding.newTabContainerScrollView.isVisible) {
            enableViewPagerInput()
        }
    }

    private fun beginRootTransition() {
        TransitionManager.beginDelayedTransition(
            binding.root,
            ChangeBounds().apply {
                duration = ROOT_TRANSITION_DURATION
                excludeTarget(R.id.inputScreenButtonsContainer, true)
                excludeTarget(R.id.viewPager, true)
                excludeTarget(RecyclerView::class.java, true)
            },
        )
    }

    private fun animateFavoritesVisibility(show: Boolean) {
        val scrollView = binding.newTabContainerScrollView
        if (show) showFavorites(scrollView) else hideFavorites(scrollView)
    }

    private fun showFavorites(scrollView: View) {
        scrollView.alpha = 0f
        scrollView.translationY = 0f
        scrollView.isVisible = true
        scrollView.bringToFront()
        scrollView.animate()
            .alpha(1f)
            .setDuration(FAVORITES_ANIMATION_DURATION)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun hideFavorites(scrollView: View) {
        scrollView.animate()
            .alpha(0f)
            .setDuration(FAVORITES_ANIMATION_DURATION)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                scrollView.isVisible = false
                scrollView.alpha = 1f
                scrollView.translationY = 0f
            }
            .start()
    }

    companion object {
        const val LOGO_ANIMATION_DURATION = 350L
        const val LOGO_MAX_FRAME = 15
        const val LOGO_FADE_DURATION = 200L
        const val FAVORITES_ANIMATION_DURATION = 200L
        const val ROOT_TRANSITION_DURATION = 150L
        const val OVERLAY_ANIMATION_DURATION = 200L
    }
}
