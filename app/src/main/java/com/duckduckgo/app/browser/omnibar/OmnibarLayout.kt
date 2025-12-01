/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.content.Context
import android.util.AttributeSet
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.animations.AddressBarTrackersAnimationFeatureToggle
import com.duckduckgo.app.browser.api.OmnibarRepository
import com.duckduckgo.app.browser.customtabs.CustomTabPixelNames
import com.duckduckgo.app.browser.omnibar.Omnibar.InputScreenLaunchListener
import com.duckduckgo.app.browser.omnibar.Omnibar.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.Omnibar.LogoClickListener
import com.duckduckgo.app.browser.omnibar.Omnibar.TextListener
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.LaunchInputScreen
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.MoveCaretToFront
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PrivacyShield
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.addressbar.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.addressbar.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.animations.omnibaranimation.OmnibarAnimationManager
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.browser.omnibar.model.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.model.Decoration.DisableVoiceSearch
import com.duckduckgo.app.browser.omnibar.model.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.model.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.model.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.model.Decoration.QueueCookiesAnimation
import com.duckduckgo.app.browser.omnibar.model.StateChange
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.serp.logos.api.SerpEasterEggLogosToggles
import com.duckduckgo.serp.logos.api.SerpLogos
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayoutView(context, attrs, defStyle), OmnibarView {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var duckAiFeatureState: DuckAiFeatureState

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var omnibarAnimationManager: OmnibarAnimationManager

    @Inject
    lateinit var onboardingDesignExperimentManager: OnboardingDesignExperimentManager

    @Inject
    lateinit var serpLogos: SerpLogos

    @Inject
    lateinit var serpEasterEggLogosToggles: SerpEasterEggLogosToggles

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    @Inject
    lateinit var addressBarTrackersAnimationFeatureToggle: AddressBarTrackersAnimationFeatureToggle

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var omnibarRepository: OmnibarRepository

    private val lifecycleOwner: LifecycleOwner by lazy {
        requireNotNull(findViewTreeLifecycleOwner())
    }

    private val pulseAnimation: PulseAnimation by lazy {
        PulseAnimation(lifecycleOwner, onboardingDesignExperimentManager)
    }

    private var omnibarInputScreenLaunchListener: InputScreenLaunchListener? = null
    private var omnibarLogoClickedListener: LogoClickListener? = null

    private var decoration: Decoration? = null
    private var lastViewMode: Mode? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()

    private val findInPageLayoutVisibilityChangeListener =
        OnGlobalLayoutListener {
            if (isFindInPageVisible()) {
                updateFindInPageVisibility()
                if (isVisible) {
                    onFindInPageShown(viewModel.viewState.value.viewMode)
                    viewModel.onFindInPageRequested()
                } else {
                    onFindInPageHidden(viewModel.viewState.value)
                    viewModel.onFindInPageDismissed()
                }
            }
        }

    init {
        AndroidSupportInjection.inject(this)

        setType(settingsDataStore.omnibarType)
        renderPosition(animationEnabled = addressBarTrackersAnimationFeatureToggle.feature().isEnabled())
    }

    override var isScrollingEnabled: Boolean
        get() {
            return if (isAttachedToWindow) {
                viewModel.viewState.value.scrollingEnabled
            } else {
                true
            }
        }
        set(value) {
            if (isAttachedToWindow) {
                viewModel.onOmnibarScrollingEnabledChanged(value)
            }
        }

    override val isEditing: Boolean
        get() {
            return if (isAttachedToWindow) {
                viewModel.viewState.value.hasFocus
            } else {
                false
            }
        }

    override val isEditingFlow by lazy {
        viewModel.viewState.map {
            isAttachedToWindow && it.hasFocus
        }
    }

    private val viewModel: OmnibarLayoutViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[OmnibarLayoutViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val coroutineScope = requireNotNull(findViewTreeLifecycleOwner()?.lifecycleScope)

        conflatedStateJob +=
            coroutineScope.launch {
                viewModel.viewState.flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                    render(it)
                }
            }

        conflatedCommandJob +=
            coroutineScope.launch {
                viewModel.commands().flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                    processCommand(it)
                }
            }

        if (lastViewMode != null) {
            logcat { "Omnibar: onAttachedToWindow lastViewMode $lastViewMode" }
            decorateDeferred(lastViewMode!!)
            lastViewMode = null
        }

        if (decoration != null) {
            logcat { "Omnibar: onAttachedToWindow decoration $decoration" }
            decorateDeferred(decoration!!)
            decoration = null
        }

        if (stateBuffer.isNotEmpty()) {
            stateBuffer.forEach {
                reduce(it)
            }
            stateBuffer.clear()
        }

        animatorHelper.setListener(this)
        findInPage.findInPageContainer.viewTreeObserver.addOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        findInPage.findInPageContainer.viewTreeObserver.removeOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
        super.onDetachedFromWindow()
    }

    override fun setOmnibarTextListener(textListener: TextListener) {
        setTextListener(textListener)
        setOnFocusChangeListener { hasFocus, textInput ->
            viewModel.onOmnibarFocusChanged(hasFocus, textInput)
        }
        setOnBackKeyListener { viewModel.onBackKeyPressed() }
        setOnEnterPressedListener { viewModel.onEnterKeyPressed() }
        setOnTouchListener { event ->
            viewModel.onUserTouchedOmnibarTextInput(event.action)
        }
        setReplaceTextChangeListener { hasFocus, textInput, queryCleared, deleteLastCharacter ->
            viewModel.onInputStateChanged(
                query = textInput,
                hasFocus = hasFocus,
                clearQuery = queryCleared,
                deleteLastCharacter = deleteLastCharacter,
            )
        }
        setShowSuggestionListener()
    }

    override fun setOmnibarItemPressedListener(itemPressedListener: ItemPressedListener) {
        setItemPressedListener(itemPressedListener)
        setOnClearTextButtonClickListener {
            viewModel.onClearTextButtonPressed()
        }
        setDuckAIHeaderClickListener {
            viewModel.onDuckAiHeaderClicked()
        }
    }

    override fun setLogoClickListener(logoClickListener: LogoClickListener) {
        omnibarLogoClickedListener = logoClickListener
    }

    private fun render(viewState: ViewState) {
        setButtonTransitionSet(
            boundsDuration = omnibarAnimationManager.getChangeBoundsDuration(),
            tension = omnibarAnimationManager.getTension(),
            fadeDuration = omnibarAnimationManager.getFadeDuration(),
        )
        render(
            viewState = viewState,
            containerClicked = {
                pixel.fire(CustomTabPixelNames.CUSTOM_TABS_ADDRESS_BAR_CLICKED)
                pixel.fire(CustomTabPixelNames.CUSTOM_TABS_ADDRESS_BAR_CLICKED_DAILY, type = PixelType.Daily())
            },
            daxIconClicked = {
                viewModel.onLogoClicked()
            },
            newCustomTabEnabled = omnibarRepository.isNewCustomTabEnabled,
            omnibarAnimationEnabled = omnibarAnimationManager.isFeatureEnabled(),
            easterEggLogoEnabled = serpEasterEggLogosToggles.feature().isEnabled(),
            privacyShieldAnimationListener = { shieldIconView, privacyShieldState, viewMode, useLightAnimation ->
                privacyShieldView.setAnimationView(shieldIconView, privacyShieldState, viewMode, useLightAnimation)
            },
        )
        if (viewState.viewMode !is ViewMode.CustomTab && viewState.viewMode !is ViewMode.DuckAI) {
            isScrollingEnabled = viewState.scrollingEnabled
            renderPulseAnimation(viewState)
        }
    }

    fun processCommand(command: Command) {
        when (command) {
            Command.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is StartCookiesAnimation -> {
                createCookiesAnimation(command.isCosmetic)
            }

            MoveCaretToFront -> {
                moveCaretToFront()
            }

            is StartTrackersAnimation -> {
                startTrackersAnimation(command.entities, command.isCustomTab)
            }

            is LaunchInputScreen -> {
                omnibarInputScreenLaunchListener?.launchInputScreen(query = command.query)
            }

            is Command.EasterEggLogoClicked -> {
                onLogoClicked(command.url)
            }

            is Command.FocusInputField -> {
                omnibarTextInput.postDelayed(
                    {
                        omnibarTextInput.requestFocus()
                    },
                    200,
                )
            }
        }
    }

    override fun decorate(decoration: Decoration) {
        logcat { "Omnibar: decorate $decoration" }
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            /* TODO (cbarreiro): This is a temporary solution to prevent one-time decorations causing mode to be lost when view is not attached
             *  As a long-term solution, we should move mode to StateChange, and only have one-time decorations here
             */
            if (decoration is Mode) {
                val lastMode = lastViewMode?.viewMode
                if (lastMode !is ViewMode.CustomTab) {
                    lastViewMode = decoration
                }
                this.decoration = null
            } else if (this.decoration == null) {
                this.decoration = decoration
            }
        }
    }

    private fun decorateDeferred(decoration: Decoration) {
        when (decoration) {
            is Mode -> {
                viewModel.onViewModeChanged(decoration.viewMode)
            }

            is PrivacyShieldChanged -> {
                viewModel.onPrivacyShieldChanged(decoration.privacyShield)
            }

            Decoration.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is LaunchTrackersAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is LaunchCookiesAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is QueueCookiesAnimation -> {
                createCookiesAnimation(isCosmetic = decoration.isCosmetic, enqueueAnimation = true)
            }

            is ChangeCustomTabTitle -> {
                viewModel.onCustomTabTitleUpdate(decoration)
            }

            is HighlightOmnibarItem -> {
                viewModel.onHighlightItem(decoration)
            }

            is DisableVoiceSearch -> {
                viewModel.onVoiceSearchDisabled(decoration.url)
            }
        }
    }

    override fun reduce(stateChange: StateChange) {
        if (isAttachedToWindow) {
            reduceDeferred(stateChange)
        } else {
            logcat { "Omnibar: reduce not attached saving $stateChange" }
            this.stateBuffer.add(stateChange)
        }
    }

    private fun reduceDeferred(stateChange: StateChange) {
        viewModel.onExternalStateChange(stateChange)
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val targetView =
            if (viewState.highlightFireButton.isHighlighted() && viewState.showFireIcon) {
                fireIconImageView
            } else if (viewState.highlightPrivacyShield.isHighlighted() && viewState.leadingIconState == PrivacyShield) {
                placeholder
            } else {
                null
            }

        if (targetView != null) {
            if (pulseAnimation.isActive) {
                pulseAnimation.stop()
            }
            doOnLayout {
                pulseAnimation.playOn(targetView = targetView)
            }
        } else {
            pulseAnimation.stop()
        }
    }

    override fun isPulseAnimationPlaying() = pulseAnimation.isActive

    private fun createCookiesAnimation(
        isCosmetic: Boolean,
        enqueueAnimation: Boolean = false,
    ) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.createCookiesAnimation(
                context,
                omnibarViews(),
                shieldViews(),
                animatedIconBackgroundView,
                cookieAnimation,
                sceneRoot,
                isCosmetic,
                enqueueAnimation,
            )
        }
    }

    private fun cancelAddressBarAnimations() {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.cancelAnimations(omnibarViews())
        }
    }

    private fun startTrackersAnimation(events: List<Entity>?, isCustomTab: Boolean) {
        if (!isCustomTab) {
            if (addressBarTrackersAnimationFeatureToggle.feature().isEnabled()) {
                animatorHelper.startAddressBarTrackersAnimation(
                    context = context,
                    addressBarTrackersBlockedAnimationShieldIcon = addressBarTrackersBlockedAnimationShieldIcon,
                    sceneRoot = sceneRoot,
                    animatedIconBackgroundView = animatedIconBackgroundView,
                    omnibarViews = omnibarViews(),
                    shieldViews = shieldViews(),
                    entities = events,
                )
            } else {
                animatorHelper.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = shieldIcon,
                    trackersAnimationView = trackersAnimation,
                    omnibarViews = omnibarViews(),
                    entities = events,
                )
            }
        } else if (omnibarRepository.isNewCustomTabEnabled) {
            val (animationBackgroundColor, useLightAnimation) = animationBackground()
            if (addressBarTrackersAnimationFeatureToggle.feature().isEnabled()) {
                animatorHelper.startAddressBarTrackersAnimation(
                    context = context,
                    addressBarTrackersBlockedAnimationShieldIcon = newCustomTabToolbarContainer.addressBarTrackersBlockedAnimationShieldIcon,
                    sceneRoot = newCustomTabToolbarContainer.customTabSceneRoot,
                    animatedIconBackgroundView = newCustomTabToolbarContainer.animatedIconBackgroundView,
                    omnibarViews = customTabViews(),
                    shieldViews = customTabShieldViews(),
                    entities = events,
                    customBackgroundColor = animationBackgroundColor,
                )
            } else {
                animatorHelper.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = newCustomTabToolbarContainer.customTabShieldIcon,
                    trackersAnimationView = newCustomTabToolbarContainer.trackersAnimation,
                    omnibarViews = customTabViews(),
                    entities = events,
                    useLightAnimation = useLightAnimation,
                )
            }
        }
    }

    private fun onLogoClicked(url: String) {
        omnibarLogoClickedListener?.onClick(url)
    }

    override fun setDraftTextIfNtpOrSerp(query: String) {
        viewModel.setDraftTextIfNtpOrSerp(query)
    }

    override fun setInputScreenLaunchListener(listener: InputScreenLaunchListener) {
        omnibarInputScreenLaunchListener = listener
        omnibarTextInputClickCatcher.setOnClickListener {
            viewModel.onTextInputClickCatcherClicked()
        }
    }

    override fun isOmnibarScrollingEnabled(): Boolean = isScrollingEnabled
}
