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

package com.duckduckgo.browser.ui.omnibar

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
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
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.duckduckgo.app.browser.omnibar.InputScreenLaunchListener
import com.duckduckgo.app.browser.omnibar.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.LogoClickListener
import com.duckduckgo.app.browser.omnibar.OmnibarBehaviour
import com.duckduckgo.app.browser.omnibar.OmnibarTextState
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.omnibar.TextListener
import com.duckduckgo.app.browser.omnibar.TrackersAnimatorListener
import com.duckduckgo.app.browser.omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.browser.ui.databinding.IncludeFindInPageBinding
import com.duckduckgo.browser.ui.databinding.IncludeNewCustomTabToolbarBinding
import com.duckduckgo.browser.ui.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.browser.ui.omnibar.animations.OmnibarAnimationManager
import com.duckduckgo.browser.ui.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.browser.ui.omnibar.model.LeadingIconState
import com.duckduckgo.browser.ui.omnibar.model.OmnibarLayoutCommand
import com.duckduckgo.browser.ui.omnibar.model.OmnibarLayoutViewState
import com.duckduckgo.browser.ui.omnibar.model.StateChange
import com.duckduckgo.browser.ui.tabs.TabSwitcherButton
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.duckduckgo.serp.logos.api.SerpEasterEggLogoAnimator
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors.isColorLight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import logcat.logcat
import com.duckduckgo.mobile.android.R as CommonR

class OmnibarLayout @JvmOverloads constructor(
    context: Context,
    override val omnibarType: OmnibarType,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle),
    OmnibarView,
    OmnibarBehaviour,
    TrackersAnimatorListener {

    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0,
    ) : this(context, OmnibarType.SINGLE_TOP, attrs, defStyle)

    data class TransitionState(
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

    // Dependencies set externally (replaces @Inject fields)
    var privacyShieldView: PrivacyShieldAnimationHelper? = null
    var animatorHelper: BrowserTrackersAnimatorHelper? = null
    var omnibarAnimationManager: OmnibarAnimationManager? = null
    var callback: OmnibarLayoutCallback? = null
    var isNewCustomTabEnabled: Boolean = false
    var isEasterEggFavouriteEnabled: Boolean = false

    // Behavior configuration (set by the host before layout)
    var viewsExemptedFromOffset: Set<Int> = emptySet()
    var browserWebViewId: Int = 0
    var viewIDsExemptedFromForceOffset: Set<Int> = emptySet()

    private var previousTransitionState: TransitionState? = null

    private val lifecycleOwner: LifecycleOwner by lazy {
        requireNotNull(findViewTreeLifecycleOwner())
    }

    private val pulseAnimation: PulseAnimation by lazy {
        PulseAnimation(lifecycleOwner)
    }

    private var omnibarTextListener: TextListener? = null
    private var omnibarItemPressedListener: ItemPressedListener? = null
    private var omnibarInputScreenLaunchListener: InputScreenLaunchListener? = null
    private var omnibarLogoClickedListener: LogoClickListener? = null

    private var decoration: Decoration? = null
    private var lastViewMode: Decoration.Mode? = null
    private var stateBuffer: MutableList<StateChange> = mutableListOf()
    private var customTabToolbarColor: Int = 0
    private var lastAnimatedLogoUrl: String? = null
    private var easterEggLogoAnimator: ObjectAnimator? = null

    private val _isEditing = MutableStateFlow(false)

    private val omnibarCardShadow: MaterialCardView by lazy { findViewById(R.id.omniBarContainerShadow) }
    private val iconsContainer: View by lazy { findViewById(R.id.iconsContainer) }
    private val shieldIconPulseAnimationContainer: View by lazy { findViewById(R.id.shieldIconPulseAnimationContainer) }
    private val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    private val customTabToolbarContainerWrapper: ViewGroup by lazy { findViewById(R.id.customTabToolbarContainerWrapper) }
    private val leadingIconContainer: View by lazy { findViewById(R.id.omnibarIconContainer) }
    val duckAIHeader: View by lazy { findViewById(R.id.duckAIHeader) }
    val duckAISidebar: View by lazy { findViewById(R.id.duckAiSidebar) }

    private var isFindInPageVisible = false
    private val findInPageLayoutVisibilityChangeListener =
        OnGlobalLayoutListener {
            val isVisible = findInPage.findInPageContainer.isVisible
            if (isFindInPageVisible != isVisible) {
                isFindInPageVisible = isVisible
                if (isVisible) {
                    onFindInPageShown()
                } else {
                    onFindInPageHidden()
                }
            }
        }

    private val omnibarCardMarginTop by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginTop)
    }

    private val omnibarCardMarginBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginBottom)
    }

    private var focusAnimator: ValueAnimator? = null

    init {
        inflate(context, R.layout.view_omnibar, this)

        renderPosition()

        if (Build.VERSION.SDK_INT >= 28) {
            omnibarCardShadow.addBottomShadow()
        }
    }

    val findInPage: IncludeFindInPageBinding by lazy {
        IncludeFindInPageBinding.bind(findViewById(R.id.findInPage))
    }
    override val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    val aiChatMenu: View? by lazy { findViewById(R.id.aiChatIconMenu) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    val browserMenuHighlight: View by lazy { findViewById(R.id.browserMenuHighlight) }
    internal val animatedIconBackgroundView: View by lazy { findViewById(R.id.animatedIconBackgroundView) }
    internal val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    internal val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    override val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    override val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    internal val toolbarContainer: ViewGroup by lazy { findViewById(R.id.toolbarContainer) }
    internal val customTabToolbarContainer by lazy {
        IncludeCustomTabToolbarBinding.bind(
            findViewById(R.id.customTabToolbarContainer),
        )
    }
    internal val newCustomTabToolbarContainer by lazy {
        IncludeNewCustomTabToolbarBinding.bind(
            findViewById(R.id.newCustomTabToolbarContainer),
        )
    }
    internal val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    override val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    internal val addressBarTrackersBlockedAnimationShieldIcon: LottieAnimationView by lazy {
        findViewById(R.id.addressBarTrackersBlockedAnimationShieldIcon)
    }
    val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    internal val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    override val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    internal val globeIcon: ImageView by lazy { findViewById(R.id.globeIcon) }
    internal val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    internal val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    internal val placeholder: View by lazy { findViewById(R.id.placeholder) }
    internal val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    internal val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    internal val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }
    internal val omniBarButtonTransitionSet: TransitionSet by lazy {
        TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(
                ChangeBounds().apply {
                    duration = omnibarAnimationManager?.getChangeBoundsDuration() ?: 200L
                    interpolator = OvershootInterpolator(omnibarAnimationManager?.getTension() ?: 0f)
                },
            )
            addTransition(
                Fade().apply {
                    duration = omnibarAnimationManager?.getFadeDuration() ?: 200L
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
    private val omnibarTextInputClickCatcher: View by lazy { findViewById(R.id.omnibarTextInputClickCatcher) }

    internal fun omnibarViews(): List<View> =
        listOf(
            clearTextButton,
            omnibarTextInput,
            searchIcon,
        )

    internal fun customTabViews(): List<View> =
        listOf(
            newCustomTabToolbarContainer.customTabDomain,
        )

    internal fun shieldViews(): List<View> =
        listOf(
            shieldIcon,
        )

    internal fun customTabShieldViews(): List<View> =
        listOf(
            newCustomTabToolbarContainer.customTabShieldIcon,
        )

    override var isScrollingEnabled: Boolean
        get() {
            return if (isAttachedToWindow) {
                callback?.currentViewState?.scrollingEnabled ?: true
            } else {
                true
            }
        }
        set(value) {
            if (isAttachedToWindow) {
                callback?.onOmnibarScrollingEnabledChanged(value)
            }
        }

    override val isEditing: Boolean
        get() {
            return if (isAttachedToWindow) {
                callback?.currentViewState?.hasFocus ?: false
            } else {
                false
            }
        }

    override val isEditingFlow: Flow<Boolean> = _isEditing

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    private val conflatedStateJob = ConflatedJob()

    private var lastSeenPrivacyShield: com.duckduckgo.app.global.model.PrivacyShield? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

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

        animatorHelper?.setListener(this)
        findInPage.findInPageContainer.viewTreeObserver.addOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
    }

    override fun onDetachedFromWindow() {
        conflatedStateJob.cancel()
        focusAnimator?.cancel()
        findInPage.findInPageContainer.viewTreeObserver.removeOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun setOmnibarTextListener(textListener: TextListener) {
        omnibarTextListener = textListener

        omnibarTextInput.onFocusChangeListener =
            OnFocusChangeListener { _, hasFocus: Boolean ->
                if (isAttachedToWindow) {
                    callback?.onOmnibarFocusChanged(hasFocus, omnibarTextInput.text.toString())
                    omnibarTextListener?.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
                }
            }

        omnibarTextInput.onBackKeyListener =
            object : KeyboardAwareEditText.OnBackKeyListener {
                override fun onBackKey(): Boolean {
                    if (isAttachedToWindow) {
                        callback?.onBackKeyPressed()
                        omnibarTextListener?.onBackKeyPressed()
                    }
                    return false
                }
            }

        omnibarTextInput.setOnEditorActionListener(
            TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if (isAttachedToWindow) {
                    if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        callback?.onEnterKeyPressed()
                        omnibarTextListener?.onEnterPressed()
                        return@OnEditorActionListener true
                    }
                }
                false
            },
        )

        omnibarTextInput.setOnTouchListener { _, event ->
            if (isAttachedToWindow) {
                callback?.onUserTouchedOmnibarTextInput(event.action)
            }
            false
        }

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                var clearQuery = false
                var deleteLastCharacter = false

                override fun afterTextChanged(editable: Editable) {
                    if (isAttachedToWindow) {
                        callback?.onInputStateChanged(
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

        omnibarTextInput.showSuggestionsListener =
            object : ShowSuggestionsListener {
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

    override fun setOmnibarItemPressedListener(itemPressedListener: ItemPressedListener) {
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
                callback?.onFireIconPressed(isPulseAnimationPlaying())
            }
            omnibarItemPressedListener?.onFireButtonPressed()
        }
        browserMenu.setOnClickListener {
            omnibarItemPressedListener?.onBrowserMenuPressed()
        }
        aiChatMenu?.setOnClickListener {
            callback?.onDuckChatButtonPressed()
            omnibarItemPressedListener?.onDuckChatButtonPressed()
        }
        shieldIcon.setOnClickListener {
            if (isAttachedToWindow) {
                callback?.onPrivacyShieldButtonPressed()
            }
            omnibarItemPressedListener?.onPrivacyShieldPressed()
        }
        clearTextButton.setOnClickListener {
            if (isAttachedToWindow) {
                callback?.onClearTextButtonPressed()
            }
        }
        voiceSearchButton.setOnClickListener {
            omnibarItemPressedListener?.onVoiceSearchPressed()
        }
        backIcon.setOnClickListener {
            callback?.onBackButtonPressed()
            omnibarItemPressedListener?.onBackButtonPressed()
        }
        duckAIHeader.setOnClickListener {
            callback?.onDuckAiHeaderClicked()
        }
        duckAISidebar.setOnClickListener {
            omnibarItemPressedListener?.onDuckAISidebarButtonPressed()
        }
    }

    override fun setLogoClickListener(logoClickListener: LogoClickListener) {
        omnibarLogoClickedListener = logoClickListener
    }

    fun render(viewState: OmnibarLayoutViewState) {
        _isEditing.value = isAttachedToWindow && viewState.hasFocus

        when (viewState.viewMode) {
            is ViewMode.CustomTab -> {
                renderCustomTabMode(viewState, viewState.viewMode)
            }

            is ViewMode.DuckAI -> {
                renderDuckAiMode(viewState)
            }

            else -> {
                renderBrowserMode(viewState)
            }
        }

        duckAIHeader.isVisible = viewState.showDuckAIHeader

        leadingIconContainer.isGone = viewState.showDuckAIHeader
        omnibarTextInput.isGone = viewState.showDuckAIHeader

        if (viewState.leadingIconState == LeadingIconState.PrivacyShield) {
            renderPrivacyShield(viewState.privacyShield, viewState.viewMode)
        } else {
            lastSeenPrivacyShield = null
        }

        if (viewState.hasFocus || isFindInPageVisible) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }

        omnibarCardShadow.isGone = viewState.viewMode is ViewMode.CustomTab && !isFindInPageVisible

        renderButtons(viewState)

        omniBarButtonTransitionSet.doOnEnd {
            omnibarTextInput.requestLayout()
        }
    }

    private fun renderPosition() {
        if (omnibarType == OmnibarType.SINGLE_TOP || omnibarType == OmnibarType.SPLIT) {
            if (Build.VERSION.SDK_INT < 28) {
                omnibarCardShadow.cardElevation = 2f.toPx(context)
            }
        } else {
            omnibarCardShadow.updateLayoutParams {
                flipOmnibarMargins()
            }

            iconsContainer.updateLayoutParams {
                flipOmnibarMargins()
            }

            duckAISidebar.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    topMargin = omnibarCardMarginBottom
                    bottomMargin = omnibarCardMarginTop
                }
            }

            shieldIconPulseAnimationContainer.updateLayoutParams {
                flipOmnibarMargins()
            }

            shieldIconPulseAnimationContainer.setPadding(
                shieldIconPulseAnimationContainer.paddingLeft,
                shieldIconPulseAnimationContainer.paddingTop,
                shieldIconPulseAnimationContainer.paddingRight,
                6.toPx(),
            )

            if (Build.VERSION.SDK_INT < 28) {
                omnibarCardShadow.cardElevation = 0.5f.toPx(context)
            }
        }
    }

    fun processCommand(command: OmnibarLayoutCommand) {
        when (command) {
            OmnibarLayoutCommand.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is OmnibarLayoutCommand.StartCookiesAnimation -> {
                createCookiesAnimation(command.isCosmetic)
            }

            OmnibarLayoutCommand.MoveCaretToFront -> {
                moveCaretToFront()
            }

            is OmnibarLayoutCommand.StartTrackersAnimation -> {
                startTrackersAnimation(command.entities, command.isCustomTab, command.isAddressBarTrackersAnimationEnabled)
            }

            is OmnibarLayoutCommand.LaunchInputScreen -> {
                omnibarInputScreenLaunchListener?.launchInputScreen(query = command.query)
            }

            is OmnibarLayoutCommand.EasterEggLogoClicked -> {
                onLogoClicked(command.url)
            }

            is OmnibarLayoutCommand.FocusInputField -> {
                omnibarTextInput.postDelayed(
                    {
                        omnibarTextInput.requestFocus()
                    },
                    200,
                )
            }

            is OmnibarLayoutCommand.CancelEasterEggLogoAnimation -> cancelEasterEggLogoAnimation()
        }
    }

    private fun moveCaretToFront() {
        omnibarTextInput.post {
            omnibarTextInput.setSelection(0)
        }
    }

    private fun renderTabIcon(viewState: OmnibarLayoutViewState) {
        if (viewState.shouldUpdateTabsCount) {
            tabsMenu.count = viewState.tabCount
            tabsMenu.hasUnread = viewState.hasUnreadTabs
        }
    }

    private fun renderOmnibarText(viewState: OmnibarLayoutViewState) {
        if (viewState.updateOmnibarText) {
            omnibarTextInput.setText(viewState.omnibarText)
        }
    }

    private fun renderLeadingIconState(viewState: OmnibarLayoutViewState) {
        when (val leadingIconState = viewState.leadingIconState) {
            LeadingIconState.Search -> {
                searchIcon.show()
                shieldIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            LeadingIconState.PrivacyShield -> {
                shieldIcon.show()
                searchIcon.gone()
                daxIcon.gone()
                globeIcon.gone()
                duckPlayerIcon.gone()
            }

            LeadingIconState.Dax -> {
                with(daxIcon) {
                    setOnClickListener(null)
                    show()
                    Glide
                        .with(this)
                        .load(CommonR.drawable.ic_ddg_logo)
                        .transition(withCrossFade())
                        .placeholder(daxIcon.drawable)
                        .listener(null)
                        .into(this)
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

            is LeadingIconState.EasterEggLogo -> {
                daxIcon.show()
                val logoUrl = leadingIconState.logoUrl
                Glide
                    .with(daxIcon)
                    .load(logoUrl)
                    .placeholder(daxIcon.drawable)
                    .transition(withCrossFade())
                    .listener(EasterEggLogoListener(leadingIconState, logoUrl))
                    .into(daxIcon)
                daxIcon.setOnClickListener {
                    callback?.onLogoClicked()
                }
                globeIcon.gone()
                shieldIcon.gone()
                searchIcon.gone()
                duckPlayerIcon.gone()
            }
        }
    }

    fun renderButtons(viewState: OmnibarLayoutViewState) {
        val isAnimationEnabled = omnibarAnimationManager?.isFeatureEnabled() ?: false
        val newTransitionState =
            TransitionState(
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

        if (isAnimationEnabled && previousTransitionState != null &&
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

        if (isAnimationEnabled) {
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

    private fun renderBrowserMode(viewState: OmnibarLayoutViewState) {
        renderOutline(viewState.hasFocus)
        renderOmnibarText(viewState)
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

        isScrollingEnabled = viewState.scrollingEnabled

        if (viewState.isAddressBarTrackersAnimationEnabled) {
            shieldIconPulseAnimationContainer.updateLayoutParams {
                (this as MarginLayoutParams).apply {
                    marginStart = 1.toPx()
                }
            }
        }

        renderTabIcon(viewState)
        renderPulseAnimation(viewState)

        renderLeadingIconState(viewState)

        omnibarTextInput.hint = context.getString(R.string.search)
    }

    private fun renderDuckAiMode(viewState: OmnibarLayoutViewState) {
        logcat { "Omnibar: renderDuckAiMode $viewState" }
        renderTabIcon(viewState)
        renderOmnibarText(viewState)
        pageLoadingIndicator.isVisible = viewState.isLoading
        voiceSearchButton.isVisible = viewState.showVoiceSearch
    }

    private fun renderCustomTabMode(
        viewState: OmnibarLayoutViewState,
        viewMode: ViewMode.CustomTab,
    ) {
        logcat { "Omnibar: renderCustomTabMode $viewState" }
        configureCustomTabOmnibar(viewMode)
        renderCustomTab(viewMode)
    }

    override fun decorate(decoration: Decoration) {
        logcat { "Omnibar: decorate $decoration" }
        if (isAttachedToWindow) {
            decorateDeferred(decoration)
        } else {
            if (decoration is Decoration.Mode) {
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
            is Decoration.Mode -> {
                callback?.onViewModeChanged(decoration.viewMode)
            }

            is Decoration.PrivacyShieldChanged -> {
                callback?.onPrivacyShieldChanged(decoration.privacyShield)
            }

            Decoration.CancelAnimations -> {
                cancelAddressBarAnimations()
            }

            is Decoration.LaunchTrackersAnimation -> {
                callback?.onAnimationStarted(decoration)
            }

            is Decoration.LaunchCookiesAnimation -> {
                callback?.onAnimationStarted(decoration)
            }

            is Decoration.QueueCookiesAnimation -> {
                createCookiesAnimation(isCosmetic = decoration.isCosmetic, enqueueAnimation = true)
            }

            is Decoration.ChangeCustomTabTitle -> {
                callback?.onCustomTabTitleUpdate(decoration)
            }

            is Decoration.HighlightOmnibarItem -> {
                callback?.onHighlightItem(decoration)
            }

            is Decoration.DisableVoiceSearch -> {
                callback?.onVoiceSearchDisabled(decoration.url)
            }

            is Decoration.CancelEasterEggLogoAnimation -> callback?.onCancelAddressBarAnimations()
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
        callback?.onExternalStateChange(stateChange)
    }

    private fun renderPulseAnimation(viewState: OmnibarLayoutViewState) {
        val targetView =
            if (viewState.highlightFireButton.isHighlighted() && viewState.showFireIcon) {
                fireIconImageView
            } else if (viewState.highlightPrivacyShield.isHighlighted() && viewState.leadingIconState == LeadingIconState.PrivacyShield) {
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
        animatorHelper?.createCookiesAnimation(
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

    private fun cancelAddressBarAnimations() {
        animatorHelper?.cancelAnimations(omnibarViews())
    }

    private fun cancelEasterEggLogoAnimation() {
        easterEggLogoAnimator?.cancel()
        easterEggLogoAnimator = null
        daxIcon.rotation = 0f
    }

    private fun startTrackersAnimation(
        events: List<Entity>?,
        isCustomTab: Boolean,
        isAddressBarTrackersAnimationEnabled: Boolean,
    ) {
        if (!isCustomTab) {
            if (isAddressBarTrackersAnimationEnabled) {
                animatorHelper?.startAddressBarTrackersAnimation(
                    context = context,
                    addressBarTrackersBlockedAnimationShieldIcon = addressBarTrackersBlockedAnimationShieldIcon,
                    sceneRoot = sceneRoot,
                    animatedIconBackgroundView = animatedIconBackgroundView,
                    omnibarViews = omnibarViews(),
                    shieldViews = shieldViews(),
                    entities = events,
                )
            } else {
                animatorHelper?.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = shieldIcon,
                    trackersAnimationView = trackersAnimation,
                    omnibarViews = omnibarViews(),
                    entities = events,
                )
            }
        } else if (isNewCustomTabEnabled) {
            val animationBackgroundColor = calculateAnimationBackgroundColor(customTabToolbarColor)
            if (isAddressBarTrackersAnimationEnabled) {
                animatorHelper?.startAddressBarTrackersAnimation(
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
                animatorHelper?.startTrackersAnimation(
                    context = context,
                    shieldAnimationView = newCustomTabToolbarContainer.customTabShieldIcon,
                    trackersAnimationView = newCustomTabToolbarContainer.trackersAnimation,
                    omnibarViews = customTabViews(),
                    entities = events,
                    useLightAnimation = isColorLight(animationBackgroundColor),
                )
            }
        }
    }

    private fun renderPrivacyShield(
        privacyShieldState: com.duckduckgo.app.global.model.PrivacyShield,
        viewMode: ViewMode,
    ) {
        if (privacyShieldState != lastSeenPrivacyShield) {
            lastSeenPrivacyShield = privacyShieldState
            val shieldIconView =
                if (viewMode is ViewMode.Browser) {
                    shieldIcon
                } else if (isNewCustomTabEnabled) {
                    newCustomTabToolbarContainer.customTabShieldIcon
                } else {
                    customTabToolbarContainer.customTabShieldIcon
                }

            val useLightAnimation = if (viewMode is ViewMode.CustomTab &&
                isNewCustomTabEnabled &&
                !isDefaultToolbarColor(customTabToolbarColor)
            ) {
                isColorLight(customTabToolbarColor)
            } else {
                null
            }

            privacyShieldView?.setAnimationView(shieldIconView, privacyShieldState, viewMode, useLightAnimation)
        }
    }

    private fun renderOutline(enabled: Boolean) {
        omniBarContainer.isPressed = enabled
    }

    private fun configureCustomTabOmnibar(customTab: ViewMode.CustomTab) {
        if (isNewCustomTabEnabled) {
            customTabToolbarColor = customTab.toolbarColor
            with(newCustomTabToolbarContainer) {
                if (!customTabToolbar.isVisible) {
                    if (omnibarType == OmnibarType.SINGLE_BOTTOM) {
                        newCustomTabToolbarContainer.customTabToolbar.updateLayoutParams {
                            flipOmnibarMargins()
                        }
                    }

                    if (customTab.toolbarColor != 0 && !isDefaultToolbarColor(customTab.toolbarColor)) {
                        toolbar.background = customTab.toolbarColor.toDrawable()
                        toolbarContainer.background = customTab.toolbarColor.toDrawable()

                        val foregroundColor = calculateCustomTabForegroundColor(customTab.toolbarColor)
                        customTabCloseIcon.setColorFilter(foregroundColor)
                        browserMenuImageView.setColorFilter(foregroundColor)
                        customTabDomain.setTextColor(foregroundColor)
                    }
                    val animationBackgroundColor = calculateAnimationBackgroundColor(customTab.toolbarColor)

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

                    customTabToolbar.setOnClickListener {
                        callback?.onCustomTabAddressBarClicked()
                    }

                    daxIcon.setOnClickListener {
                        callback?.onCustomTabDaxClicked()
                    }

                    customTabSceneRoot.setOnClickListener {
                        callback?.onCustomTabTrackerAnimationClicked()
                    }
                }
            }
        } else {
            with(customTabToolbarContainer) {
                if (!customTabToolbar.isVisible) {
                    customTabCloseIcon.setOnClickListener {
                        omnibarItemPressedListener?.onCustomTabClosePressed()
                    }

                    customTabShieldIcon.setOnClickListener { _ ->
                        omnibarItemPressedListener?.onCustomTabPrivacyDashboardPressed()
                    }

                    toolbar.background = customTab.toolbarColor.toDrawable()
                    toolbarContainer.background = customTab.toolbarColor.toDrawable()

                    omniBarContainer.hide()
                    customTabToolbar.show()

                    browserMenu.isVisible = true

                    val foregroundColor = calculateCustomTabBackgroundColor(customTab.toolbarColor)
                    customTabCloseIcon.setColorFilter(foregroundColor)
                    customTabDomain.setTextColor(foregroundColor)
                    customTabDomainOnly.setTextColor(foregroundColor)
                    customTabTitle.setTextColor(foregroundColor)
                    browserMenuImageView.setColorFilter(foregroundColor)
                }
            }
        }
    }

    private fun ViewGroup.LayoutParams.flipOmnibarMargins() {
        (this as MarginLayoutParams).apply {
            topMargin = omnibarCardMarginBottom
            bottomMargin = omnibarCardMarginTop
        }
    }

    private fun isDefaultToolbarColor(color: Int): Boolean {
        val defaultLightColor = ContextCompat.getColor(context, CommonR.color.background_background_light)
        val defaultDarkColor = ContextCompat.getColor(context, CommonR.color.background_background_dark)
        return color == defaultLightColor || color == defaultDarkColor
    }

    private fun calculateAddressBarColor(mainToolbarColor: Int): Int {
        return if (isColorLight(mainToolbarColor)) {
            val targetSaturation = 0.55f
            val targetLightness = 0.90f
            val hsl = floatArrayOf(0f, 0f, 0f)
            ColorUtils.colorToHSL(mainToolbarColor, hsl)
            if (hsl[1] < 0.01f) {
                hsl[1] = 0f
            } else {
                hsl[1] = targetSaturation
            }
            hsl[2] = targetLightness
            ColorUtils.HSLToColor(hsl)
        } else {
            ColorUtils.blendARGB(mainToolbarColor, Color.WHITE, 0.20f)
        }
    }

    private fun calculateAnimationBackgroundColor(mainToolbarColor: Int): Int {
        val blendColor = if (isColorLight(mainToolbarColor)) Color.BLACK else Color.WHITE
        return ColorUtils.blendARGB(mainToolbarColor, blendColor, 0.12f)
    }

    private fun renderCustomTab(viewMode: ViewMode.CustomTab) {
        logcat { "Omnibar: updateCustomTabTitle $decoration" }

        if (isNewCustomTabEnabled) {
            with(newCustomTabToolbarContainer) {
                viewMode.domain?.let {
                    customTabDomain.text = viewMode.domain
                    customTabDomain.show()
                }

                customTabShieldIcon.isInvisible = viewMode.showDuckPlayerIcon
                customTabDuckPlayerIcon.isVisible = viewMode.showDuckPlayerIcon
            }
        } else {
            with(customTabToolbarContainer) {
                viewMode.domain?.let {
                    customTabDomain.text = viewMode.domain
                    customTabDomainOnly.text = viewMode.domain
                    customTabDomain.show()
                    customTabDomainOnly.show()
                }

                viewMode.title?.let {
                    customTabTitle.text = viewMode.title
                    customTabTitle.show()
                    customTabDomainOnly.hide()
                }

                customTabShieldIcon.isInvisible = viewMode.showDuckPlayerIcon
                customTabDuckPlayerIcon.isVisible = viewMode.showDuckPlayerIcon
            }
        }
    }

    private fun calculateCustomTabBackgroundColor(color: Int): Int {
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

    private fun isColorLight(color: Int): Boolean {
        if (color == 0) {
            return !(context as DuckDuckGoActivity).isDarkThemeEnabled()
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return true
        }

        val luminance = ColorUtils.calculateLuminance(color)
        return luminance > 0.5
    }

    private fun applyFindInPageTheme(toolbarColor: Int) {
        val backgroundColor = calculateAddressBarColor(toolbarColor)
        val isColorLight = isColorLight(backgroundColor)

        with(findInPage) {
            findInPageContainer.background = backgroundColor.toDrawable()

            val foregroundColor = if (isColorLight) Color.BLACK else Color.WHITE
            val hintColor = if (foregroundColor == Color.WHITE) {
                Color.argb(153, 255, 255, 255)
            } else {
                Color.argb(153, 0, 0, 0)
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

    private fun onLogoClicked(url: String) {
        omnibarLogoClickedListener?.onClick(url)
    }

    override fun measuredHeight(): Int = measuredHeight

    override fun height(): Int = height

    override fun getTranslation(): Float = translationY

    override fun setTranslation(y: Float) {
        translationY = y
    }

    override var isUiLocked: Boolean = false

    override fun isOmnibarScrollingEnabled(): Boolean = isScrollingEnabled && !isUiLocked

    override fun isBottomNavEnabled(): Boolean = false

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> =
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> TopAppBarBehavior(context, this, viewsExemptedFromOffset, browserWebViewId)
            OmnibarType.SINGLE_BOTTOM -> BottomAppBarBehavior(context, this, viewIDsExemptedFromForceOffset, browserWebViewId)
        }

    override fun setExpanded(expanded: Boolean) {
        when (omnibarType) {
            OmnibarType.SINGLE_TOP, OmnibarType.SPLIT -> super.setExpanded(expanded)
            OmnibarType.SINGLE_BOTTOM -> (behavior as BottomAppBarBehavior).setExpanded(expanded)
        }
    }

    override fun disableViewStateSaving() {
        customTabToolbarContainer.customTabDomainOnly.isSaveEnabled = false
        customTabToolbarContainer.customTabDomain.isSaveEnabled = false
        customTabToolbarContainer.customTabTitle.isSaveEnabled = false
        customTabToolbarContainer.customTabDuckPlayerIcon.isSaveEnabled = false
        customTabToolbarContainer.customTabCloseIcon.isSaveEnabled = false
        customTabToolbarContainer.customTabShieldIcon.isSaveEnabled = false

        findInPage.findInPageContainer.isSaveEnabled = false
        findInPage.findInPageMatches.isSaveEnabled = false
        findInPage.nextSearchTermButton.isSaveEnabled = false
        findInPage.previousSearchTermButton.isSaveEnabled = false
        findInPage.findIcon.isSaveEnabled = false

        newCustomTabToolbarContainer.customTabCloseIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.trackersAnimation.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabShieldIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.addressBarTrackersBlockedAnimationShieldIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabDuckPlayerIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.daxIcon.isSaveEnabled = false
        newCustomTabToolbarContainer.customTabDomain.isSaveEnabled = false

        pageLoadingIndicator.isSaveEnabled = false
        shieldIcon.isSaveEnabled = false
        omnibarTextInput.isSaveEnabled = false
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

    override fun setMenuIcon(resId: Int) {
        ContextCompat.getDrawable(context, resId)?.let {
            browserMenuImageView.setImageDrawable(it)
        }
    }

    override fun onAnimationFinished() {
        omnibarTextListener?.onTrackersCountFinished()
    }

    override fun setDraftTextIfNtpOrSerp(query: String) {
        callback?.setDraftTextIfNtpOrSerp(query)
    }

    private fun enableTextInputClickCatcher(enabled: Boolean) {
        omnibarTextInputClickCatcher.isVisible = enabled

        omnibarTextInput.apply {
            isEnabled = !enabled
            isFocusable = !enabled
            isFocusableInTouchMode = !enabled
        }
    }

    override fun setInputScreenLaunchListener(listener: InputScreenLaunchListener) {
        omnibarInputScreenLaunchListener = listener
        omnibarTextInputClickCatcher.setOnClickListener {
            callback?.onTextInputClickCatcherClicked()
        }
    }

    private fun animateOmnibarFocusedState(focused: Boolean) {
        // temporarily disable focus animation
    }

    private fun onFindInPageShown() {
        omniBarContentContainer.hide()
        customTabToolbarContainerWrapper.hide()
        val currentViewState = callback?.currentViewState
        if (currentViewState?.viewMode is ViewMode.CustomTab) {
            val toolbarColor = (currentViewState.viewMode as ViewMode.CustomTab).toolbarColor

            if (!isDefaultToolbarColor(toolbarColor)) {
                applyFindInPageTheme(toolbarColor)
            }
            omniBarContainer.show()
            browserMenu.gone()
        }
        animateOmnibarFocusedState(focused = true)
        callback?.onFindInPageRequested()
    }

    private fun onFindInPageHidden() {
        omniBarContentContainer.show()
        customTabToolbarContainerWrapper.show()
        val currentViewState = callback?.currentViewState
        if (currentViewState?.viewMode is ViewMode.CustomTab) {
            omniBarContainer.hide()
            browserMenu.isVisible = currentViewState.showBrowserMenu
        }
        if (currentViewState?.hasFocus != true) {
            animateOmnibarFocusedState(focused = false)
        }
        callback?.onFindInPageDismissed()
    }

    override fun show() {
        visibility = View.VISIBLE
    }

    override fun gone() {
        visibility = View.GONE
    }

    private inner class EasterEggLogoListener(
        private val leadingIconState: LeadingIconState.EasterEggLogo,
        private val logoUrl: String,
    ) : RequestListener<Drawable> {

        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean = false

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            if (!leadingIconState.isFavourite && logoUrl != lastAnimatedLogoUrl) {
                if (isEasterEggFavouriteEnabled) {
                    lastAnimatedLogoUrl = logoUrl
                    daxIcon.postDelayed(
                        {
                            easterEggLogoAnimator = SerpEasterEggLogoAnimator.playWiggle(daxIcon)
                        },
                        EASTER_EGG_ANIMATION_DELAY_MS,
                    )
                }
            }
            return false
        }
    }

    companion object {
        private const val EASTER_EGG_ANIMATION_DELAY_MS = 1000L
    }
}
