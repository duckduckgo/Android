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
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultCodes
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentInputScreenBinding
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
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEARCH
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEND
import com.duckduckgo.duckchat.impl.inputscreen.ui.tabs.InputScreenPagerAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputModeWidget
import com.duckduckgo.duckchat.impl.inputscreen.ui.view.InputScreenButtons
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
                viewModel.onPageScrolled(position, positionOffset)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    viewModel.onScrollStateIdle()
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

        inputModeWidget =
            InputModeWidget(requireContext()).also {
                it.id = R.id.inputModeWidget
            }
        inputScreenButtons = InputScreenButtons(requireContext())

        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        params?.query?.let { query ->
            inputModeWidget.provideInitialText(query)
        }

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

        if (useTopBar) {
            binding.inputModeWidgetContainerTop.addView(inputModeWidget)
            binding.inputModeWidgetContainerTop.addView(contentSeparator)
            binding.inputScreenButtonsContainer.addView(inputScreenButtons)
            inputScreenButtons.transformButtonsToFloating()
        } else {
            inputModeWidget.setInputScreenButtons(inputScreenButtons)
            binding.inputModeWidgetContainerBottom.addView(inputModeWidget)
            binding.inputModeWidgetContainerBottom.addView(contentSeparator)
        }

        configureViewPager()
        params?.tabs?.let { tabs ->
            configureOmnibar(tabs)
        }
        configureVoice()
        configureObservers()
        configureLogoAnimation()

        inputModeWidget.init()

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
            .onEach {
                val isSearchMode = binding.viewPager.currentItem == 0
                binding.ddgLogoContainer.isVisible =
                    if (isSearchMode) {
                        it.showSearchLogo
                    } else {
                        it.showChatLogo
                    }

                binding.ddgLogo.progress = if (isSearchMode) 0f else 1f

                inputScreenButtons.setSendButtonVisible(it.submitButtonVisible)
                inputScreenButtons.setNewLineButtonVisible(it.newLineButtonVisible)
            }.launchIn(lifecycleScope)

        viewModel.visibilityState
            .map { it.actionButtonsContainerVisible }
            .distinctUntilChanged()
            .onEach {
                inputModeWidget.setInputScreenButtonsVisible(it)
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
        }
    }

    private fun configureViewPager() {
        pagerAdapter = InputScreenPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun configureOmnibar(tabs: Int) =
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
                binding.ddgLogoContainer.isVisible = viewModel.visibilityState.value.showSearchLogo
            }
            onChatSelected = {
                binding.viewPager.setCurrentItem(1, true)
                viewModel.onChatSelected()
                viewModel.onChatInputTextChanged(inputModeWidget.text)
                binding.ddgLogoContainer.apply {
                    val showChatLogo = viewModel.visibilityState.value.showChatLogo
                    val showSearchLogo = viewModel.visibilityState.value.showSearchLogo
                    isVisible = showChatLogo
                    if (showChatLogo && !showSearchLogo) {
                        alpha = 0f
                        animate().alpha(1f).setDuration(LOGO_FADE_DURATION).start()
                    }
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
            }
            onInputFieldClicked = {
                viewModel.onInputFieldTouched()
            }
            onTabTapped = { index ->
                viewModel.onTabTapped(index)
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

    private fun configureVoice() {
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) {
            when (it) {
                is VoiceRecognitionSuccess -> {
                    inputModeWidget.submitMessage(it.result)
                }
                is SearchCancelled -> {}
                is VoiceSearchDisabled -> {
                    viewModel.onVoiceSearchDisabled()
                }
            }
        }
        viewModel.visibilityState
            .onEach {
                inputScreenButtons.setVoiceButtonVisible(it.voiceInputButtonVisible)
            }.launchIn(lifecycleScope)
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
            voiceSearchLauncher.launch(requireActivity())
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
        }

    private fun setLogoProgress(targetProgress: Float) {
        binding.ddgLogo.progress = targetProgress
    }

    private fun animateLogoToProgress(targetProgress: Float) {
        logoAnimator?.cancel()
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

    override fun onDestroyView() {
        logoAnimator?.cancel()
        logoAnimator = null
        binding.ddgLogo.clearAnimation()
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume()
    }

    companion object {
        const val LOGO_ANIMATION_DURATION = 350L
        const val LOGO_MAX_FRAME = 15
        const val LOGO_FADE_DURATION = 200L
    }
}
