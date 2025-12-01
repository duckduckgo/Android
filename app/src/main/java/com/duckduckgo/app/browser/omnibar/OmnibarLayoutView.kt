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

package com.duckduckgo.app.browser.omnibar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.Editable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import androidx.core.transition.doOnEnd
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.databinding.IncludeNewCustomTabToolbarBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.Omnibar.OmnibarTextState
import com.duckduckgo.app.browser.omnibar.Omnibar.TextListener
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.EasterEggLogo
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.LeadingIconState.PrivacyShield
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.animations.addressbar.TrackersAnimatorListener
import com.duckduckgo.app.global.view.renderIfChanged
import com.duckduckgo.browser.ui.tabs.TabSwitcherButton
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import logcat.logcat
import com.duckduckgo.app.global.model.PrivacyShield as PrivacyShieldState
import com.duckduckgo.mobile.android.R as CommonR

abstract class OmnibarLayoutView(
    context: Context,
    val attrs: AttributeSet? = null,
    val defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle),
    OmnibarBehaviour,
    TrackersAnimatorListener {

    constructor(context: Context) : this(context = context, attrs = null, defStyle = 0)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context = context, attrs = attrs, defStyle = 0)

    private var omnibarTextListener: TextListener? = null
    private var omnibarItemPressedListener: ItemPressedListener? = null

    protected val omnibarCardShadow: MaterialCardView by lazy { findViewById(R.id.omniBarContainerShadow) }
    protected val iconsContainer: View by lazy { findViewById(R.id.iconsContainer) }
    protected val shieldIconPulseAnimationContainer: View by lazy { findViewById(R.id.shieldIconPulseAnimationContainer) }
    protected val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    protected val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    protected val customTabToolbarContainerWrapper: ViewGroup by lazy { findViewById(R.id.customTabToolbarContainerWrapper) }
    protected val leadingIconContainer: View by lazy { findViewById(R.id.omnibarIconContainer) }
    protected val duckAIHeader: View by lazy { findViewById(R.id.duckAIHeader) }
    protected val duckAISidebar: View by lazy { findViewById(R.id.duckAiSidebar) }
    val findInPage: IncludeFindInPageBinding by lazy {
        IncludeFindInPageBinding.bind(findViewById(R.id.findInPage))
    }
    val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    protected val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    protected val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    protected val aiChatMenu: View? by lazy { findViewById(R.id.aiChatIconMenu) }
    protected val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    protected val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    protected val browserMenuHighlight: View by lazy { findViewById(R.id.browserMenuHighlight) }
    protected val animatedIconBackgroundView: View by lazy { findViewById(R.id.animatedIconBackgroundView) }
    protected val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    protected val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    protected val toolbarContainer: ViewGroup by lazy { findViewById(R.id.toolbarContainer) }
    protected val customTabToolbarContainer by lazy {
        IncludeCustomTabToolbarBinding.bind(
            findViewById(R.id.customTabToolbarContainer),
        )
    }
    protected val newCustomTabToolbarContainer by lazy {
        IncludeNewCustomTabToolbarBinding.bind(
            findViewById(R.id.newCustomTabToolbarContainer),
        )
    }
    protected val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    protected val addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView by lazy {
        findViewById(R.id.addressBarTrackersBlockedAnimationShieldIcon)
    }
    protected val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    protected val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    protected val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    protected val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    protected val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    protected val placeholder: View by lazy { findViewById(R.id.placeholder) }
    protected val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    protected val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    protected val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }
    protected val omnibarTextInputClickCatcher: View by lazy { findViewById(R.id.omnibarTextInputClickCatcher) }
    protected val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    private val omnibarCardMarginTop by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginTop)
    }

    private val omnibarCardMarginBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginBottom)
    }

    private data class TransitionState(
        val showClearButton: Boolean,
        val showVoiceSearch: Boolean,
        val showTabsMenu: Boolean,
        val showFireIcon: Boolean,
        val showBrowserMenu: Boolean,
        val showBrowserMenuHighlight: Boolean,
        val showChatMenu: Boolean,
        val showSpacer: Boolean,
        val showDuckSidebar: Boolean,
    )

    // Internal state
    var omnibarType: OmnibarType = OmnibarType.SINGLE_TOP
    protected var omniBarButtonTransitionSet: TransitionSet? = null
    private var isFindInPageVisible = false
    private var lastSeenPrivacyShield: PrivacyShieldState? = null
    private var customTabToolbarColor: Int = 0
    private var previousTransitionState: TransitionState? = null

    init {
        inflate(context, R.layout.view_omnibar, this)

        if (Build.VERSION.SDK_INT >= 28) {
            omnibarCardShadow.addBottomShadow()
        }
    }

    internal fun omnibarViews(): List<View> = listOf(clearTextButton, omnibarTextInput, searchIcon)
    internal fun customTabViews(): List<View> = listOf(newCustomTabToolbarContainer.customTabDomain)
    internal fun shieldViews(): List<View> = listOf(shieldIcon)
    internal fun customTabShieldViews(): List<View> = listOf(newCustomTabToolbarContainer.customTabShieldIcon)

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> =
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> TopAppBarBehavior(context, this)
            OmnibarType.SINGLE_BOTTOM -> BottomAppBarBehavior(context, this)
        }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> super.setExpanded(expanded)
            OmnibarType.SINGLE_BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    override fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> super.setExpanded(expanded, animate)
            OmnibarType.SINGLE_BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    fun show() {
        visibility = View.VISIBLE
    }

    fun gone() {
        visibility = View.GONE
    }

    fun moveCaretToFront() {
        omnibarTextInput.post {
            omnibarTextInput.setSelection(0)
        }
    }

    fun setType(omnibarType: OmnibarType) {
        this.omnibarType = omnibarType
    }

    fun isFindInPageVisible(): Boolean {
        val isVisible = findInPage.findInPageContainer.isVisible
        return isFindInPageVisible != isVisible
    }

    fun updateFindInPageVisibility() {
        isFindInPageVisible = findInPage.findInPageContainer.isVisible
    }

    fun onFindInPageShown(viewModel: ViewMode) {
        omniBarContentContainer.hide()
        customTabToolbarContainerWrapper.hide()
        if (viewModel is ViewMode.CustomTab) {
            val toolbarColor = viewModel.toolbarColor

            if (!isDefaultToolbarColor(toolbarColor)) {
                applyFindInPageTheme(toolbarColor)
            }
            omniBarContainer.show()
            browserMenu.gone()
        }
        animateOmnibarFocusedState(focused = true)
    }

    fun onFindInPageHidden(viewState: ViewState) {
        omniBarContentContainer.show()
        customTabToolbarContainerWrapper.show()
        if (viewState.viewMode is ViewMode.CustomTab) {
            omniBarContainer.hide()
            browserMenu.isVisible = viewState.showBrowserMenu
        }
        if (!viewState.hasFocus) {
            animateOmnibarFocusedState(focused = false)
        }
    }

    fun setButtonTransitionSet(
        boundsDuration: Long,
        tension: Float,
        fadeDuration: Long,
    ) {
        if (omniBarButtonTransitionSet != null) {
            // ignore
            return
        }
        this.omniBarButtonTransitionSet = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(
                ChangeBounds().apply {
                    duration = boundsDuration
                    interpolator = OvershootInterpolator(tension)
                },
            )
            addTransition(
                Fade().apply {
                    duration = fadeDuration
                    addTarget(clearTextButton)
                    addTarget(voiceSearchButton)
                    addTarget(fireIconMenu)
                    addTarget(tabsMenu)
                    addTarget(aiChatMenu)
                    addTarget(browserMenu)
                    addTarget(duckAISidebar)
                },
            )
        }
    }

    fun render(
        viewState: ViewState,
        containerClicked: () -> Unit,
        daxIconClicked: () -> Unit,
        newCustomTabEnabled: Boolean,
        omnibarAnimationEnabled: Boolean,
        easterEggLogoEnabled: Boolean,
        privacyShieldAnimationListener: (LottieAnimationView, PrivacyShieldState, ViewMode, Boolean?) -> Unit,
    ) {
        when (viewState.viewMode) {
            is ViewMode.CustomTab -> {
                if (newCustomTabEnabled) {
                    configureCustomTab(viewState.viewMode.toolbarColor, omnibarType == OmnibarType.SINGLE_BOTTOM, containerClicked)
                    renderCustomTabMode(viewState.viewMode.domain, viewState.viewMode.showDuckPlayerIcon)
                } else {
                    configureCustomTab(viewState.viewMode.toolbarColor)
                    renderCustomTabMode(viewState.viewMode.domain, viewState.viewMode.title, viewState.viewMode.showDuckPlayerIcon)
                }
            }

            is ViewMode.DuckAI -> {
                renderDuckAiMode(viewState)
            }

            else -> {
                renderBrowserMode(viewState, easterEggLogoEnabled, daxIconClicked)
            }
        }

        duckAIHeader.isVisible = viewState.showDuckAIHeader

        leadingIconContainer.isGone = viewState.showDuckAIHeader
        omnibarTextInput.isGone = viewState.showDuckAIHeader

        if (viewState.leadingIconState == PrivacyShield) {
            renderPrivacyShield(
                viewMode = viewState.viewMode,
                privacyShieldState = viewState.privacyShield,
                newCustomTabEnabled = newCustomTabEnabled,
                privacyShieldAnimationListener = privacyShieldAnimationListener,
            )
        } else {
            lastSeenPrivacyShield = null
        }

        if (viewState.hasFocus || isFindInPageVisible) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }

        omnibarCardShadow.isGone = viewState.viewMode is ViewMode.CustomTab && !isFindInPageVisible

        renderButtons(viewState, omnibarAnimationEnabled)

        omniBarButtonTransitionSet?.doOnEnd {
            omnibarTextInput.requestLayout()
        }
    }

    fun renderPosition(animationEnabled: Boolean) {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.OmnibarLayout, defStyle, 0)
        val omnibarType = OmnibarType.entries[attr.getInt(R.styleable.OmnibarLayout_omnibarPosition, 0)]
        val isTopPosition = omnibarType == OmnibarType.SINGLE_TOP || omnibarType == OmnibarType.SPLIT
        if (isTopPosition) {
            renderTopPosition(animationEnabled)
        } else {
            renderBottomPosition(animationEnabled)
        }
    }

    fun setTextListener(textListener: TextListener) {
        omnibarTextListener = textListener
    }

    fun setOnFocusChangeListener(listener: (hasFocus: Boolean, textInput: String) -> Unit) {
        omnibarTextInput.onFocusChangeListener = OnFocusChangeListener { _, hasFocus: Boolean ->
            if (isAttachedToWindow) {
                listener.invoke(hasFocus, omnibarTextInput.text.toString())
                omnibarTextListener?.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
            }
        }
    }

    fun setOnBackKeyListener(listener: () -> Unit) {
        omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
            override fun onBackKey(): Boolean {
                if (isAttachedToWindow) {
                    listener.invoke()
                    omnibarTextListener?.onBackKeyPressed()
                }
                return false
            }
        }
    }

    fun setOnEnterPressedListener(listener: () -> Unit) {
        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (isAttachedToWindow) {
                    if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        listener.invoke()
                        omnibarTextListener?.onEnterPressed()
                        return@OnEditorActionListener true
                    }
                }
                false
            },
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setOnTouchListener(listener: (event: MotionEvent) -> Unit) {
        omnibarTextInput.setOnTouchListener { _, event ->
            if (isAttachedToWindow) {
                listener.invoke(event)
            }
            false
        }
    }

    fun setReplaceTextChangeListener(listener: (hasFocus: Boolean, textInput: String, queryCleared: Boolean, deleteLastCharacter: Boolean) -> Unit) {
        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                var clearQuery = false
                var deleteLastCharacter = false

                override fun afterTextChanged(editable: Editable) {
                    if (isAttachedToWindow) {
                        listener.invoke(
                            omnibarTextInput.hasFocus(),
                            omnibarTextInput.text.toString(),
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

                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    logcat { "Omnibar: $count characters beginning at $start are about to be replaced by new text with length $after" }
                    clearQuery = start == 0 && after == 0
                    deleteLastCharacter = count == 1 && clearQuery
                }
            },
        )
    }

    fun setShowSuggestionListener() {
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

    fun setItemPressedListener(itemPressedListener: ItemPressedListener) {
        omnibarItemPressedListener = itemPressedListener
        tabsMenu.setOnClickListener {
            omnibarItemPressedListener?.onTabsButtonPressed()
        }
        tabsMenu.setOnLongClickListener {
            omnibarItemPressedListener?.onTabsButtonLongPressed()
            return@setOnLongClickListener true
        }
        fireIconMenu.setOnClickListener {
            omnibarItemPressedListener?.onFireButtonPressed()
        }
        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }
        aiChatMenu?.setOnClickListener {
            omnibarItemPressedListener?.onDuckChatButtonPressed()
        }
        shieldIcon.setOnClickListener {
            omnibarItemPressedListener?.onPrivacyShieldPressed()
        }
        voiceSearchButton.setOnClickListener {
            omnibarItemPressedListener?.onVoiceSearchPressed()
        }
        backIcon.setOnClickListener {
            omnibarItemPressedListener?.onBackButtonPressed()
        }
        duckAISidebar.setOnClickListener {
            omnibarItemPressedListener?.onDuckAISidebarButtonPressed()
        }
    }

    fun setOnClearTextButtonClickListener(listener: () -> Unit) {
        clearTextButton.setOnClickListener {
            listener.invoke()
        }
    }

    fun setDuckAIHeaderClickListener(listener: () -> Unit) {
        duckAIHeader.setOnClickListener {
            listener.invoke()
        }
    }

    fun animationBackground(): Pair<Int, Boolean?> {
        return if (!isDefaultToolbarColor(customTabToolbarColor)) {
            val background = calculateAnimationBackgroundColor(customTabToolbarColor)
            Pair(background, isColorLight(background))
        } else {
            Pair(customTabToolbarColor, null)
        }
    }

    private fun renderBrowserMode(
        viewState: ViewState,
        easterEggLogoEnabled: Boolean,
        daxIconClicked: () -> Unit,
    ) {
        renderOutline(viewState.hasFocus)
        if (viewState.updateOmnibarText) {
            omnibarTextInput.setText(viewState.omnibarText)
        }
        if (viewState.expanded) {
            setExpanded(true, viewState.expandedAnimated)
        }

        if (viewState.isLoading) {
            pageLoadingIndicator.show()
        }
        smoothProgressAnimator.onNewProgress(viewState.loadingProgress) {
            if (!viewState.isLoading) {
                pageLoadingIndicator.hide()
            }
        }

        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabCount
            tabsMenu.hasUnread = viewState.hasUnreadTabs
        }

        renderLeadingIconState(viewState.leadingIconState, easterEggLogoEnabled, daxIconClicked)

        omnibarTextInput.hint = context.getString(R.string.search)
    }

    private fun renderDuckAiMode(viewState: ViewState) {
        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabCount
            tabsMenu.hasUnread = viewState.hasUnreadTabs
        }
        pageLoadingIndicator.isVisible = viewState.isLoading
        voiceSearchButton.isVisible = viewState.showVoiceSearch
    }

    private fun renderCustomTabMode(
        domain: String?,
        showDuckPlayerIcon: Boolean,
    ) {
        with(newCustomTabToolbarContainer) {
            domain?.let {
                customTabDomain.text = domain
                customTabDomain.show()
            }

            customTabShieldIcon.isInvisible = showDuckPlayerIcon
            customTabDuckPlayerIcon.isVisible = showDuckPlayerIcon
        }
    }

    @Deprecated("Depends newCustomTab toggle value")
    private fun renderCustomTabMode(
        domain: String?,
        title: String?,
        showDuckPlayerIcon: Boolean,
    ) = with(customTabToolbarContainer) {
        domain?.let {
            customTabDomain.text = domain
            customTabDomainOnly.text = domain
            customTabDomain.show()
            customTabDomainOnly.show()
        }

        title?.let {
            customTabTitle.text = title
            customTabTitle.show()
            customTabDomainOnly.hide()
        }

        customTabShieldIcon.isInvisible = showDuckPlayerIcon
        customTabDuckPlayerIcon.isVisible = showDuckPlayerIcon
    }

    private fun renderPrivacyShield(
        viewMode: ViewMode,
        privacyShieldState: PrivacyShieldState,
        newCustomTabEnabled: Boolean,
        privacyShieldAnimationListener: (LottieAnimationView, PrivacyShieldState, ViewMode, Boolean?) -> Unit,
    ) {
        renderIfChanged(privacyShieldState, lastSeenPrivacyShield) {
            lastSeenPrivacyShield = privacyShieldState
            val shieldIconView =
                if (viewMode is ViewMode.Browser) {
                    shieldIcon
                } else if (newCustomTabEnabled) {
                    newCustomTabToolbarContainer.customTabShieldIcon
                } else {
                    customTabToolbarContainer.customTabShieldIcon
                }

            // For new custom tabs, determine light/dark variant based on container color
            // Shield sits on secondaryToolbarColor background, so invert: light bg needs dark shield
            val useLightAnimation = if (viewMode is ViewMode.CustomTab && newCustomTabEnabled && !isDefaultToolbarColor(customTabToolbarColor)) {
                val secondaryToolbarColor = calculateAddressBarColor(customTabToolbarColor)
                isColorLight(secondaryToolbarColor)
            } else {
                null // Use default theme-based selection
            }

            privacyShieldAnimationListener.invoke(shieldIconView, privacyShieldState, viewMode, useLightAnimation)
        }
    }

    fun renderButtons(
        viewState: ViewState,
        omnibarAnimationEnabled: Boolean,
    ) {
        val newTransitionState = TransitionState(
            showClearButton = viewState.showClearButton,
            showVoiceSearch = viewState.showVoiceSearch,
            showTabsMenu = viewState.showTabsMenu && !viewState.showFindInPage,
            showFireIcon = viewState.showFireIcon && !viewState.showFindInPage,
            showBrowserMenu = viewState.showBrowserMenu && !viewState.showFindInPage,
            showBrowserMenuHighlight = viewState.showBrowserMenuHighlight,
            showChatMenu = viewState.showChatMenu,
            showSpacer = viewState.showClearButton || viewState.showVoiceSearch,
            showDuckSidebar = viewState.showDuckAISidebar,
        )

        if (omnibarAnimationEnabled && previousTransitionState != null &&
            (
                newTransitionState.showFireIcon != previousTransitionState?.showFireIcon ||
                    newTransitionState.showTabsMenu != previousTransitionState?.showTabsMenu ||
                    newTransitionState.showBrowserMenu != previousTransitionState?.showBrowserMenu ||
                    newTransitionState.showDuckSidebar != previousTransitionState?.showDuckSidebar
                )
        ) {
            TransitionManager.beginDelayedTransition(toolbarContainer, omniBarButtonTransitionSet)
        }

        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        tabsMenu.isVisible = newTransitionState.showTabsMenu
        fireIconMenu.isVisible = newTransitionState.showFireIcon
        browserMenu.isVisible = newTransitionState.showBrowserMenu
        browserMenuHighlight.isVisible = newTransitionState.showBrowserMenuHighlight
        aiChatMenu?.isVisible = newTransitionState.showChatMenu
        aiChatDivider.isVisible = (viewState.showVoiceSearch || viewState.showClearButton) && viewState.showChatMenu
        duckAISidebar.isVisible = newTransitionState.showDuckSidebar

        if (omnibarAnimationEnabled) {
            toolbarContainer.requestLayout()
        }

        previousTransitionState = newTransitionState

        enableTextInputClickCatcher(viewState.showTextInputClickCatcher)

        val showBackArrow = viewState.hasFocus
        if (showBackArrow) {
            backIcon.show()
            searchIcon.gone()
            shieldIcon.gone()
            daxIcon.gone()
            globeIcon.gone()
            duckPlayerIcon.gone()
        } else {
            backIcon.hide()
        }
    }

    private fun configureCustomTab(
        toolbarColor: Int,
        isSingleBottom: Boolean,
        containerClicked: () -> Unit,
    ) = with(newCustomTabToolbarContainer) {
        if (customTabToolbar.isVisible) {
            return@with
        }
        if (isSingleBottom) {
            newCustomTabToolbarContainer.customTabToolbar.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    topMargin = omnibarCardMarginBottom
                    bottomMargin = omnibarCardMarginTop
                }
            }
        }

        val animationBackgroundColor: Int
        if (toolbarColor != 0 && !isDefaultToolbarColor(toolbarColor)) {
            toolbar.background = toolbarColor.toDrawable()
            toolbarContainer.background = toolbarColor.toDrawable()

            val foregroundColor = calculateCustomTabForegroundColor(toolbarColor)
            val secondaryToolbarColor = calculateAddressBarColor(toolbarColor)
            customTabCloseIcon.setColorFilter(foregroundColor)
            customTabDomain.setTextColor(calculateCustomTabForegroundColor(secondaryToolbarColor))
            customToolbarContainer.setCardBackgroundColor(secondaryToolbarColor)
            browserMenuImageView.setColorFilter(foregroundColor)

            customToolbarContainer.setOnClickListener {
                containerClicked.invoke()
            }

            animationBackgroundColor = calculateAnimationBackgroundColor(toolbarColor)
        } else {
            animationBackgroundColor = toolbarColor
        }

        val iconBackground = newCustomTabToolbarContainer.animatedIconBackgroundView.background
        if (iconBackground is android.graphics.drawable.GradientDrawable) {
            val mutatedDrawable = iconBackground.mutate() as android.graphics.drawable.GradientDrawable
            mutatedDrawable.setColor(animationBackgroundColor)
            mutatedDrawable.setStroke(1, animationBackgroundColor)
        }

        browserMenu.isVisible = true

        omniBarContainer.hide()
        customTabToolbar.show()

        customTabCloseIcon.setOnClickListener {
            omnibarItemPressedListener?.onCustomTabClosePressed()
        }

        customTabShieldIcon.setOnClickListener { _ ->
            omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
        }
    }

    @Deprecated("Depends newCustomTab toggle value")
    private fun configureCustomTab(toolbarColor: Int) = with(customTabToolbarContainer) {
        if (!customTabToolbar.isVisible) {
            customTabCloseIcon.setOnClickListener {
                omnibarItemPressedListener?.onCustomTabClosePressed()
            }

            customTabShieldIcon.setOnClickListener { _ ->
                omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
            }

            toolbar.background = toolbarColor.toDrawable()
            toolbarContainer.background = toolbarColor.toDrawable()

            omniBarContainer.hide()
            customTabToolbar.show()

            browserMenu.isVisible = true

            val foregroundColor = calculateCustomTabBackgroundColor(toolbarColor)
            customTabCloseIcon.setColorFilter(foregroundColor)
            customTabDomain.setTextColor(foregroundColor)
            customTabDomainOnly.setTextColor(foregroundColor)
            customTabTitle.setTextColor(foregroundColor)
            browserMenuImageView.setColorFilter(foregroundColor)
        }
    }

    private fun renderTopPosition(animationEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < 28) {
            omnibarCardShadow.cardElevation = 2f.toPx(context)
        }

        shieldIconPulseAnimationContainer.updateLayoutParams {
            (this as MarginLayoutParams).apply {
                if (animationEnabled) {
                    // TODO when the animation is made permanent we should add this adjustment to the actual layout
                    marginStart = 1.toPx()
                }
            }
        }
    }

    private fun renderBottomPosition(animationEnabled: Boolean) {
        // When omnibar is at the bottom, we're adding an additional space at the top
        omnibarCardShadow.updateLayoutParams {
            (this as MarginLayoutParams).apply {
                topMargin = omnibarCardMarginBottom
                bottomMargin = omnibarCardMarginTop
            }
        }

        iconsContainer.updateLayoutParams {
            (this as MarginLayoutParams).apply {
                topMargin = omnibarCardMarginBottom
                bottomMargin = omnibarCardMarginTop
            }
        }

        shieldIconPulseAnimationContainer.updateLayoutParams {
            (this as MarginLayoutParams).apply {
                topMargin = omnibarCardMarginBottom
                bottomMargin = omnibarCardMarginTop
                if (animationEnabled) {
                    // TODO when the animation is made permanent we should add this adjustment to the actual layout
                    marginStart = 1.toPx()
                }
            }
        }

        shieldIconPulseAnimationContainer.setPadding(
            shieldIconPulseAnimationContainer.paddingLeft,
            shieldIconPulseAnimationContainer.paddingTop,
            shieldIconPulseAnimationContainer.paddingRight,
            6.toPx(),
        )

        // Try to reduce the bottom omnibar material shadow when not using the custom shadow
        if (Build.VERSION.SDK_INT < 28) {
            omnibarCardShadow.cardElevation = 0.5f.toPx(context)
        }
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun renderLeadingIconState(
        iconState: LeadingIconState,
        easterEggLogoEnabled: Boolean,
        daxIconClicked: () -> Unit,
    ) {
        when (iconState) {
            LeadingIconState.Search -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            PrivacyShield -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            LeadingIconState.Dax -> {
                if (easterEggLogoEnabled) {
                    with(daxIcon) {
                        setOnClickListener(null)
                        show()
                        Glide
                            .with(this)
                            .load(CommonR.drawable.ic_ddg_logo)
                            .transition(withCrossFade())
                            .placeholder(daxIcon.drawable)
                            .into(this)
                    }
                } else {
                    daxIcon.show()
                }
                shieldIcon.gone()
                searchIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            LeadingIconState.Globe -> {
                globeIcon.show()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }

            LeadingIconState.DuckPlayer -> {
                globeIcon.gone()
                daxIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.show()
            }

            is EasterEggLogo -> {
                daxIcon.show()
                Glide
                    .with(daxIcon)
                    .load(iconState.logoUrl)
                    .placeholder(daxIcon.drawable)
                    .transition(withCrossFade())
                    .into(daxIcon)
                daxIcon.setOnClickListener { daxIconClicked.invoke() }
                globeIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }
        }
    }

    private fun applyFindInPageTheme(toolbarColor: Int) {
        val backgroundColor = calculateAddressBarColor(toolbarColor)
        val isColorLight = isColorLight(backgroundColor)

        with(findInPage) {
            findInPageContainer.background = backgroundColor.toDrawable()

            val foregroundColor = if (isColorLight) Color.BLACK else Color.WHITE
            val hintColor = if (foregroundColor == Color.WHITE) {
                Color.argb(153, 255, 255, 255) // 60% white for dark theme
            } else {
                Color.argb(153, 0, 0, 0) // 60% black for light theme
            }

            findInPageInput.setTextColor(foregroundColor)
            findInPageInput.setHintTextColor(hintColor)
            findInPageMatches.setTextColor(foregroundColor)

            listOf(
                findIcon,
                previousSearchTermButton,
                nextSearchTermButton,
                closeFindInPagePanel,
            ).forEach { imageView ->
                imageView.setColorFilter(foregroundColor)
            }
        }
    }

    private fun enableTextInputClickCatcher(enabled: Boolean) {
        omnibarTextInputClickCatcher.isVisible = enabled

        omnibarTextInput.apply {
            isEnabled = !enabled
            isFocusable = !enabled
            isFocusableInTouchMode = !enabled
        }
    }

    private fun isDefaultToolbarColor(color: Int): Boolean {
        val defaultLightColor = ContextCompat.getColor(context, CommonR.color.background_background_light)
        val defaultDarkColor = ContextCompat.getColor(context, CommonR.color.background_background_dark)
        return color == defaultLightColor || color == defaultDarkColor
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

    private fun calculateCustomTabForegroundColor(color: Int): Int {
        return if (isColorLight(color)) Color.BLACK else Color.WHITE
    }

    private fun calculateAddressBarColor(mainToolbarColor: Int): Int {
        return if (isColorLight(mainToolbarColor)) {
            val targetSaturation = 0.55f
            val targetLightness = 0.90f
            val hsl = floatArrayOf(0f, 0f, 0f)
            ColorUtils.colorToHSL(mainToolbarColor, hsl)

            // hsl[0] is Hue (H) - keep this the same to maintain color identity
            // hsl[1] is Saturation (S) - reduce for muted appearance
            // hsl[2] is Lightness (L) - increase for lighter shade

            // If the original color is grayscale (near-zero saturation),
            // keep it grayscale to maintain color identity
            if (hsl[1] < 0.01f) {
                hsl[1] = 0f // Keep saturation at 0
            } else {
                hsl[1] = targetSaturation
            }
            hsl[2] = targetLightness

            ColorUtils.HSLToColor(hsl)
        } else {
            // Use a darkened version of the main toolbar color for dark themes
            ColorUtils.blendARGB(mainToolbarColor, Color.WHITE, 0.20f)
        }
    }

    private fun calculateAnimationBackgroundColor(mainToolbarColor: Int): Int {
        return ColorUtils.blendARGB(mainToolbarColor, Color.WHITE, 0.12f)
    }

    private fun isColorLight(color: Int): Boolean {
        if (color == 0) {
            return !(context as DuckDuckGoActivity).isDarkThemeEnabled()
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return true
        }

        // Use W3C relative luminance calculation
        val luminance = ColorUtils.calculateLuminance(color)
        // Use 0.5 threshold - lighter backgrounds have higher luminance
        return luminance > 0.5
    }

    private fun animateOmnibarFocusedState(focused: Boolean) {
        // temporarily disable focus animation
    }

    override fun measuredHeight(): Int = measuredHeight

    override fun height(): Int = height

    override fun getTranslation(): Float = translationY

    override fun setTranslation(y: Float) {
        translationY = y
    }

    override fun isBottomNavEnabled(): Boolean = false

    override fun onAnimationFinished() {
        omnibarTextListener?.onTrackersCountFinished()
    }
}
