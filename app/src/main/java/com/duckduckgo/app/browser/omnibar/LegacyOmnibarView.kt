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

import android.animation.Animator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SmoothProgressAnimator
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.animations.BrowserTrackersAnimatorHelper
import com.duckduckgo.app.browser.omnibar.animations.PrivacyShieldAnimationHelper
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.TextChangedWatcher
import com.duckduckgo.app.global.view.isDifferent
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.shape.DaxBubbleCardView.EdgePosition
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.appbar.AppBarLayout
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class LegacyOmnibarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : AppBarLayout(context, attrs, defStyle) {

    data class OmnibarTextState(
        val text: String,
        val hasFocus: Boolean,
    )

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.LegacyOmnibarView, defStyle, 0)
        val omnibarPosition = EdgePosition.from(attr.getInt(R.styleable.LegacyOmnibarView_omnibarPosition, 0))

        val layout = when (omnibarPosition) {
            EdgePosition.TOP -> R.layout.view_legacy_omnibar
            EdgePosition.LEFT -> R.layout.view_legacy_omnibar_bottom
        }
        inflate(context, layout, this)
    }

    @Inject
    lateinit var omnibarScrolling: OmnibarScrolling

    @Inject
    lateinit var privacyShieldView: PrivacyShieldAnimationHelper

    @Inject
    lateinit var animatorHelper: BrowserTrackersAnimatorHelper

    val findInPage by lazy { IncludeFindInPageBinding.bind(findViewById(R.id.findInPage)) }
    val omnibarTextInput: KeyboardAwareEditText by lazy { findViewById(R.id.omnibarTextInput) }
    val tabsMenu: TabSwitcherButton by lazy { findViewById(R.id.tabsMenu) }
    val fireIconMenu: FrameLayout by lazy { findViewById(R.id.fireIconMenu) }
    val browserMenu: FrameLayout by lazy { findViewById(R.id.browserMenu) }
    val cookieDummyView: View by lazy { findViewById(R.id.cookieDummyView) }
    val cookieAnimation: LottieAnimationView by lazy { findViewById(R.id.cookieAnimation) }
    val sceneRoot: ViewGroup by lazy { findViewById(R.id.sceneRoot) }
    val omniBarContainer: View by lazy { findViewById(R.id.omniBarContainer) }
    val toolbar: Toolbar by lazy { findViewById(R.id.toolbar) }
    val toolbarContainer: View by lazy { findViewById(R.id.toolbarContainer) }
    val customTabToolbarContainer by lazy { IncludeCustomTabToolbarBinding.bind(findViewById(R.id.customTabToolbarContainer)) }
    val browserMenuImageView: ImageView by lazy { findViewById(R.id.browserMenuImageView) }
    val shieldIcon: LottieAnimationView by lazy { findViewById(R.id.shieldIcon) }
    val pageLoadingIndicator: ProgressBar by lazy { findViewById(R.id.pageLoadingIndicator) }
    val searchIcon: ImageView by lazy { findViewById(R.id.searchIcon) }
    val daxIcon: ImageView by lazy { findViewById(R.id.daxIcon) }
    val clearTextButton: ImageView by lazy { findViewById(R.id.clearTextButton) }
    val fireIconImageView: ImageView by lazy { findViewById(R.id.fireIconImageView) }
    val placeholder: View by lazy { findViewById(R.id.placeholder) }
    val voiceSearchButton: ImageView by lazy { findViewById(R.id.voiceSearchButton) }
    val spacer: View by lazy { findViewById(R.id.spacer) }
    val trackersAnimation: LottieAnimationView by lazy { findViewById(R.id.trackersAnimation) }
    val duckPlayerIcon: ImageView by lazy { findViewById(R.id.duckPlayerIcon) }

    private fun omnibarViews(): List<View> = listOf(
        clearTextButton,
        omnibarTextInput,
        searchIcon,
    )

    private val smoothProgressAnimator by lazy { SmoothProgressAnimator(pageLoadingIndicator) }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()
    }

    fun getOmnibarText(): String {
        return omnibarTextInput.text.toString()
    }

    fun showOutline(pressed: Boolean) {
        omniBarContainer.isPressed = pressed
    }

    fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        smoothProgressAnimator.onNewProgress(newProgress, onAnimationEnd)
    }

    fun addTextChangedListeners(
        onFindInPageTextChanged: (String) -> Unit,
        onOmnibarTextChanged: (OmnibarTextState) -> Unit,
        onShowSuggestions: (OmnibarTextState) -> Unit,
    ) {
        findInPage.findInPageInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    onFindInPageTextChanged(findInPage.findInPageInput.text.toString())
                }
            },
        )

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    onOmnibarTextChanged(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }
            },
        )

        omnibarTextInput.showSuggestionsListener = object : ShowSuggestionsListener {
            override fun showSuggestions() {
                onShowSuggestions(
                    OmnibarTextState(
                        omnibarTextInput.text.toString(),
                        omnibarTextInput.hasFocus(),
                    ),
                )
            }
        }
    }

    fun setScrollingEnabled(enabled: Boolean) {
        if (isAttachedToWindow) {
            if (enabled) {
                omnibarScrolling.enableOmnibarScrolling(toolbarContainer)
            } else {
                omnibarScrolling.disableOmnibarScrolling(toolbarContainer)
            }
        }
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        animatorHelper.createCookiesAnimation(
            context,
            omnibarViews(),
            cookieDummyView,
            cookieAnimation,
            sceneRoot,
            isCosmetic,
        )
    }

    fun cancelTrackersAnimation() {
        animatorHelper.cancelAnimations(omnibarViews())
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        animatorHelper.startTrackersAnimation(
            context = context,
            shieldAnimationView = shieldIcon,
            trackersAnimationView = trackersAnimation,
            omnibarViews = omnibarViews(),
            entities = events,
        )
    }

    fun setPrivacyShield(
        isCustomTab: Boolean,
        privacyShield: PrivacyShield,
    ) {
        val animationViewHolder = if (isCustomTab) {
            customTabToolbarContainer.customTabShieldIcon
        } else {
            shieldIcon
        }
        privacyShieldView.setAnimationView(animationViewHolder, privacyShield)
    }

    fun configureCustomTab(
        customTabToolbarColor: Int,
        customTabDomainText: String?,
        onTabClosePressed: () -> Unit,
        onPrivacyShieldPressed: () -> Unit,
    ) {
        omniBarContainer.hide()
        fireIconMenu.hide()
        tabsMenu.hide()

        toolbar.background = ColorDrawable(customTabToolbarColor)
        toolbarContainer.background = ColorDrawable(customTabToolbarColor)

        customTabToolbarContainer.customTabToolbar.show()

        customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            onTabClosePressed()
        }

        customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            onPrivacyShieldPressed()
        }

        customTabToolbarContainer.customTabDomain.text = customTabDomainText
        customTabToolbarContainer.customTabDomainOnly.text = customTabDomainText
        customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateBlackOrWhite(customTabToolbarColor)
        customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        customTabToolbarContainer.customTabDomainOnly.setTextColor(
            foregroundColor,
        )
        customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        browserMenuImageView.setColorFilter(foregroundColor)
    }

    private fun calculateBlackOrWhite(color: Int): Int {
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

    fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
    ) {
        customTabToolbarContainer.customTabTitle.text = title

        val redirectedDomain = url?.extractDomain()
        redirectedDomain?.let {
            customTabToolbarContainer.customTabDomain.text = redirectedDomain
        }

        customTabToolbarContainer.customTabTitle.show()
        customTabToolbarContainer.customTabDomainOnly.hide()
        customTabToolbarContainer.customTabDomain.show()
    }

    fun renderOmnibarViewState(viewState: OmnibarViewState) {
        if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
            setOmnibarText(viewState.omnibarText)
            if (viewState.forceExpand) {
                setExpanded(true, true)
            }
            if (viewState.shouldMoveCaretToEnd) {
                setOmnibarTextSelection(viewState.omnibarText.length)
            }
        }
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: OmnibarViewState,
        omnibarInput: String?,
    ) =
        (!viewState.isEditing || omnibarInput.isNullOrEmpty()) && omnibarTextInput.isDifferent(
            omnibarInput,
        )

    fun setOmnibarText(text: String) {
        omnibarTextInput.setText(text)
    }

    fun setOmnibarTextSelection(index: Int) {
        omnibarTextInput.setSelection(index)
    }

    fun renderVoiceSearch(
        viewState: BrowserViewState,
        voiceSearchPressed: () -> Unit,
    ) {
        if (viewState.showVoiceSearch) {
            voiceSearchButton.visibility = VISIBLE
            voiceSearchButton.setOnClickListener {
                voiceSearchPressed()
            }
        } else {
            voiceSearchButton.visibility = GONE
        }
    }

    fun showOmnibarTextSpacer(
        showVoiceSearch: Boolean,
        showClearButton: Boolean,
    ) {
        spacer.isVisible = showVoiceSearch && showClearButton
    }

    fun renderToolbarMenus(viewState: BrowserViewState) {
        if (viewState.browserShowing) {
            daxIcon.isVisible = viewState.showDaxIcon
            duckPlayerIcon.isVisible = viewState.showDuckPlayerIcon
            shieldIcon.isInvisible =
                !viewState.showPrivacyShield.isEnabled() || viewState.showDaxIcon || viewState.showDuckPlayerIcon
            clearTextButton.isVisible = viewState.showClearButton
            searchIcon.isVisible = viewState.showSearchIcon
        } else {
            daxIcon.isVisible = false
            duckPlayerIcon.isVisible = false
            shieldIcon.isVisible = false
            clearTextButton.isVisible = viewState.showClearButton
            searchIcon.isVisible = true
        }
    }
}

private fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
    removeTextChangedListener(textWatcher)
    addTextChangedListener(textWatcher)
}
