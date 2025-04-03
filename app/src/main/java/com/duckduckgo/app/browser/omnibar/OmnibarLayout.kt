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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.PulseAnimation
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.ChangeCustomTabTitle
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.DisableVoiceSearch
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.LaunchCookiesAnimation
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.LaunchTrackersAnimation
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.Outline
import com.duckduckgo.app.browser.omnibar.Omnibar.Decoration.PrivacyShieldChanged
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarTextState
import com.duckduckgo.app.browser.omnibar.Omnibar.StateChange
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.MoveCaretToFront
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartCookiesAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command.StartTrackersAnimation
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.tabswitcher.TabSwitcherButton
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@InjectWith(FragmentScope::class)
open class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle), OmnibarBehaviour {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private val lifecycleOwner: LifecycleOwner by lazy {
        requireNotNull(findViewTreeLifecycleOwner())
    }

    private val pulseAnimation: PulseAnimation by lazy {
        PulseAnimation(lifecycleOwner)
    }

    private var omnibarTextListener: Omnibar.TextListener? = null
    private var omnibarItemPressedListener: Omnibar.ItemPressedListener? = null

    private var decoration: Decoration? = null
    private var lastViewMode: Mode? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()

    override val findInPage by lazy { IncludeFindInPageBinding.bind(findViewById(R.id.findInPage)) }
    override val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    internal val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    internal val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    internal val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    internal val browserMenuHighlight: View by lazy { findViewById(R.id.browserMenuHighlight) }
    internal val cookieDummyView: View by lazy { findViewById(R.id.cookieDummyView) }
    internal val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    internal val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    override val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    override val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    internal val toolbarContainer: View by lazy { findViewById(R.id.toolbarContainer) }
    internal val customTabToolbarContainer by lazy {
        IncludeCustomTabToolbarBinding.bind(
            findViewById(R.id.customTabToolbarContainer),
        )
    }
    internal val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    override val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    internal val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    internal val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    internal val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    internal val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val spacer: View by lazy { findViewById(R.id.spacer) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }

    internal fun omnibarViews(): List<View> = listOf(
        clearTextButton,
        omnibarTextInput,
        searchIcon,
    )

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

    open var omnibarPosition: OmnibarPosition = OmnibarPosition.TOP

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    protected val viewModel: OmnibarLayoutViewModel by lazy {
        ViewModelProvider(
            findViewTreeViewModelStoreOwner()!!,
            viewModelFactory,
        )[OmnibarLayoutViewModel::class.java]
    }

    private val conflatedStateJob = ConflatedJob()
    private val conflatedCommandJob = ConflatedJob()

    private var lastSeenPrivacyShield: PrivacyShield? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val coroutineScope = requireNotNull(findViewTreeLifecycleOwner()?.lifecycleScope)

        conflatedStateJob += coroutineScope.launch {
            viewModel.viewState.flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                render(it)
            }
        }

        conflatedCommandJob += coroutineScope.launch {
            viewModel.commands().flowWithLifecycle(lifecycleOwner.lifecycle).collectLatest {
                processCommand(it)
            }
        }

        if (lastViewMode != null) {
            Timber.d("Omnibar: onAttachedToWindow lastViewMode $lastViewMode")
            decorateDeferred(lastViewMode!!)
            lastViewMode = null
        }

        if (decoration != null) {
            Timber.d("Omnibar: onAttachedToWindow decoration $decoration")
            decorateDeferred(decoration!!)
            decoration = null
        }

        if (stateBuffer.isNotEmpty()) {
            stateBuffer.forEach {
                reduce(it)
            }
            stateBuffer.clear()
        }
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        conflatedCommandJob.cancel()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOmnibarTextListener(textListener: Omnibar.TextListener) {
        omnibarTextListener = textListener

        omnibarTextInput.onFocusChangeListener =
            View.OnFocusChangeListener { _, hasFocus: Boolean ->
                if (isAttachedToWindow) {
                    viewModel.onOmnibarFocusChanged(hasFocus, omnibarTextInput.text.toString())
                    omnibarTextListener?.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
                }
            }

        omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                if (isAttachedToWindow) {
                    viewModel.onBackKeyPressed()
                    omnibarTextListener?.onBackKeyPressed()
                }
                return false
            }
        }

        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (isAttachedToWindow) {
                    if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        viewModel.onEnterKeyPressed()
                        omnibarTextListener?.onEnterPressed()
                        return@OnEditorActionListener true
                    }
                }
                false
            },
        )

        omnibarTextInput.setOnTouchListener { _, event ->
            if (isAttachedToWindow) {
                viewModel.onUserTouchedOmnibarTextInput(event.action)
            }
            false
        }

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                var clearQuery = false
                var deleteLastCharacter = false
                override fun afterTextChanged(editable: Editable) {
                    if (isAttachedToWindow) {
                        viewModel.onInputStateChanged(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                            clearQuery,
                            deleteLastCharacter,
                        )
                    }
                    omnibarTextListener?.onOmnibarTextChanged(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }

                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                    Timber.d("Omnibar: $count characters beginning at $start are about to be replaced by new text with length $after")
                    clearQuery = start == 0 && after == 0
                    deleteLastCharacter = count == 1 && clearQuery
                }
            },
        )

        omnibarTextInput.showSuggestionsListener = object : ShowSuggestionsListener {
            override fun showSuggestions() {
                omnibarTextListener?.onShowSuggestions(
                    OmnibarTextState(
                        omnibarTextInput.text.toString(),
                        omnibarTextInput.hasFocus(),
                    ),
                )
            }
        }
    }

    override fun setOmnibarItemPressedListener(itemPressedListener: Omnibar.ItemPressedListener) {
        omnibarItemPressedListener = itemPressedListener
        tabsMenu.setOnClickListener {
            omnibarItemPressedListener?.onTabsButtonPressed()
        }
        tabsMenu.setOnLongClickListener {
            omnibarItemPressedListener?.onTabsButtonLongPressed()
            return@setOnLongClickListener true
        }
        fireIconMenu.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onFireIconPressed(isPulseAnimationPlaying())
            }
            omnibarItemPressedListener?.onFireButtonPressed()
        }
        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }
        shieldIcon.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onPrivacyShieldButtonPressed()
            }
            omnibarItemPressedListener?.onPrivacyShieldPressed()
        }
        clearTextButton.setOnClickListener {
            if (isAttachedToWindow) {
                viewModel.onClearTextButtonPressed()
            }
        }
        voiceSearchButton.setOnClickListener {
            omnibarItemPressedListener?.onVoiceSearchPressed()
        }
    }

    open fun render(viewState: ViewState) {
        when (viewState.viewMode) {
            is CustomTab -> {
                renderCustomTabMode(viewState, viewState.viewMode)
            }

            else -> {
                renderBrowserMode(viewState)
            }
        }

        if (viewState.leadingIconState == PRIVACY_SHIELD) {
            renderPrivacyShield(viewState.privacyShield, viewState.viewMode)
        } else {
            lastSeenPrivacyShield = null
        }
        renderButtons(viewState)
    }

    open fun processCommand(command: OmnibarLayoutViewModel.Command) {
        when (command) {
            Command.CancelAnimations -> {
                cancelTrackersAnimation()
            }

            is StartTrackersAnimation -> {
                startTrackersAnimation(command.entities)
            }

            is StartCookiesAnimation -> {
                createCookiesAnimation(command.isCosmetic)
            }

            MoveCaretToFront -> {
                moveCaretToFront()
            }
        }
    }

    private fun moveCaretToFront() {
        omnibarTextInput.post {
            omnibarTextInput.setSelection(0)
        }
    }

    private fun renderTabIcon(viewState: ViewState) {
        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabCount
            tabsMenu.hasUnread = viewState.hasUnreadTabs
        }
    }

    private fun renderLeadingIconState(iconState: OmnibarLayoutViewModel.LeadingIconState) {
        when (iconState) {
            OmnibarLayoutViewModel.LeadingIconState.SEARCH -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DAX -> {
                daxIcon.show()
                shieldIcon.gone()
                searchIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.GLOBE -> {
                globeIcon.show()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DUCK_PLAYER -> {
                globeIcon.gone()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.show()
            }
        }
    }

    private fun renderButtons(viewState: ViewState) {
        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = viewState.showTabsMenu
        fireIconMenu.isVisible = viewState.showFireIcon
        browserMenu.isVisible = viewState.showBrowserMenu
        browserMenuHighlight.isVisible = viewState.showBrowserMenuHighlight
        spacer.isVisible = viewState.showVoiceSearch && viewState.showClearButton
    }

    private fun renderBrowserMode(viewState: ViewState) {
        renderOutline(viewState.hasFocus)
        if (viewState.updateOmnibarText) {
            omnibarTextInput.setText(viewState.omnibarText)
        }
        // if (viewState.expanded) {
        //     setExpanded(true, viewState.expandedAnimated)
        // }

        if (viewState.isLoading) {
            pageLoadingIndicator.show()
        }
        smoothProgressAnimator.onNewProgress(viewState.loadingProgress) {
            if (!viewState.isLoading) {
                pageLoadingIndicator.hide()
            }
        }

        isScrollingEnabled = viewState.scrollingEnabled

        renderTabIcon(viewState)
        renderPulseAnimation(viewState)

        renderLeadingIconState(viewState.leadingIconState)
    }

    private fun renderCustomTabMode(
        viewState: ViewState,
        viewMode: ViewMode.CustomTab,
    ) {
        Timber.d("Omnibar: renderCustomTabMode $viewState")
        configureCustomTabOmnibar(viewMode)
        renderCustomTab(viewMode)
    }

    override fun decorate(decoration: Decoration) {
        Timber.d("Omnibar: decorate $decoration")
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            /* TODO (cbarreiro): This is a temporary solution to prevent one-time decorations causing mode to be lost when view is not attached
             *  As a long-term solution, we should move mode to StateChange, and only have one-time decorations here
             */
            if (decoration is Mode) {
                val lastMode = lastViewMode?.viewMode
                if (lastMode !is CustomTab) {
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

            is Outline -> {
                viewModel.onOutlineEnabled(decoration.enabled)
            }

            Decoration.CancelAnimations -> {
                cancelTrackersAnimation()
            }

            is LaunchTrackersAnimation -> {
                viewModel.onAnimationStarted(decoration)
            }

            is LaunchCookiesAnimation -> {
                viewModel.onAnimationStarted(decoration)
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
            Timber.d("Omnibar: reduce not attached saving $stateChange")
            this.stateBuffer.add(stateChange)
        }
    }

    private fun reduceDeferred(stateChange: StateChange) {
        viewModel.onExternalStateChange(stateChange)
    }

    private fun renderPulseAnimation(viewState: ViewState) {
        val targetView = if (viewState.highlightFireButton.isHighlighted()) {
            fireIconImageView
        } else if (viewState.highlightPrivacyShield.isHighlighted()) {
            placeholder
        } else {
            null
        }

        if (targetView != null) {
            if (pulseAnimation.isActive) {
                pulseAnimation.stop()
            }
            doOnLayout {
                pulseAnimation.playOn(targetView)
            }
        } else {
            pulseAnimation.stop()
        }
    }

    override fun isPulseAnimationPlaying() = pulseAnimation.isActive

    private fun createCookiesAnimation(isCosmetic: Boolean) {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.createCookiesAnimation(
                context,
                omnibarViews(),
                cookieDummyView,
                cookieAnimation,
                sceneRoot,
                isCosmetic,
            )
        }
    }

    private fun cancelTrackersAnimation() {
        if (this::animatorHelper.isInitialized) {
            animatorHelper.cancelAnimations(omnibarViews())
        }
    }

    private fun startTrackersAnimation(events: List<Entity>?) {
        animatorHelper.startTrackersAnimation(
            context = context,
            shieldAnimationView = shieldIcon,
            trackersAnimationView = trackersAnimation,
            omnibarViews = omnibarViews(),
            entities = events,
        )
    }

    private fun renderPrivacyShield(
        privacyShield: PrivacyShield,
        viewMode: ViewMode,
    ) {
        renderIfChanged(privacyShield, lastSeenPrivacyShield) {
            lastSeenPrivacyShield = privacyShield
            val shieldIcon = if (viewMode is ViewMode.Browser) {
                shieldIcon
            } else {
                customTabToolbarContainer.customTabShieldIcon
            }

            privacyShieldView.setAnimationView(shieldIcon, privacyShield)
        }
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun configureCustomTabOmnibar(customTab: ViewMode.CustomTab) {
        if (!customTabToolbarContainer.customTabToolbar.isVisible) {
            customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
                omnibarItemPressedListener?.onCustomTabClosePressed()
            }

            customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
                omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
            }

            omniBarContainer.hide()

            toolbar.background = ColorDrawable(customTab.toolbarColor)
            toolbarContainer.background = ColorDrawable(customTab.toolbarColor)

            customTabToolbarContainer.customTabToolbar.show()

            browserMenu.isVisible = true

            val foregroundColor = calculateCustomTabBackgroundColor(customTab.toolbarColor)
            customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
            customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
            customTabToolbarContainer.customTabDomainOnly.setTextColor(foregroundColor)
            customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
            browserMenuImageView.setColorFilter(foregroundColor)
        }
    }

    private fun renderCustomTab(viewMode: CustomTab) {
        Timber.d("Omnibar: updateCustomTabTitle $decoration")

        viewMode.domain?.let {
            customTabToolbarContainer.customTabDomain.text = viewMode.domain
            customTabToolbarContainer.customTabDomainOnly.text = viewMode.domain
            customTabToolbarContainer.customTabDomain.show()
            customTabToolbarContainer.customTabDomainOnly.show()
        }

        viewMode.title?.let {
            customTabToolbarContainer.customTabTitle.text = viewMode.title
            customTabToolbarContainer.customTabTitle.show()
            customTabToolbarContainer.customTabDomainOnly.hide()
        }

        customTabToolbarContainer.customTabShieldIcon.isInvisible = viewMode.showDuckPlayerIcon
        customTabToolbarContainer.customTabDuckPlayerIcon.isVisible = viewMode.showDuckPlayerIcon
    }

    private fun calculateCustomTabBackgroundColor(color: Int): Int {
        // Handle the case where we did not receive a color.
        if (color == 0) {
            return if ((context as DuckDuckGoActivity).isDarkThemeEnabled()) Color.WHITE else Color.BLACK
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return Color.BLACK
        }
        val greyValue =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
        return if (greyValue < 186) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    override fun measuredHeight(): Int {
        return measuredHeight
    }

    override fun height(): Int {
        return height
    }

    override fun getTranslation(): Float {
        return translationY
    }

    override fun setTranslation(y: Float) {
        translationY = y
    }

    override fun isOmnibarScrollingEnabled(): Boolean {
        return isScrollingEnabled
    }

    override fun setVisible(visible: Boolean) {
        if (visible) {
            show()
        } else {
            gone()
        }
    }
}
