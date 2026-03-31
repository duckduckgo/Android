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

package com.duckduckgo.app.browser.nativeinput

import android.graphics.Color
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.DuckAI
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.webview.BottomOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.browser.webview.TopOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.card.MaterialCardView

sealed class DuckAiTier {
    data object Free : DuckAiTier()
    data object Paid : DuckAiTier()
    data object Unknown : DuckAiTier()
}

interface OmnibarState {
    fun isDuckAiMode(): Boolean
    fun isBrowserMode(): Boolean
    fun isOmnibarBottom(): Boolean
    fun isSplitMode(): Boolean
}

interface NativeInputOmnibarController : OmnibarState {
    fun show()
    fun hide()
    fun getText(): String
    fun hideBackground()
    fun showTransparentOmnibar()
    fun getButtonsWidth(): Int
    fun getCardView(): View?
    fun restore()
    fun forceToTop()
    fun updateTierTitle(tier: DuckAiTier, onUpgradeClicked: () -> Unit)
}

class RealNativeInputOmnibarController(
    private val omnibar: Omnibar,
    private val rootView: ViewGroup,
) : NativeInputOmnibarController {

    private var layoutListener: View.OnLayoutChangeListener? = null

    override fun isDuckAiMode(): Boolean = omnibar.viewMode == DuckAI

    override fun isBrowserMode(): Boolean = omnibar.viewMode is Omnibar.ViewMode.Browser

    override fun isOmnibarBottom(): Boolean =
        omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM

    override fun isSplitMode(): Boolean =
        omnibar.omnibarType == OmnibarType.SPLIT

    override fun show() = omnibar.show()

    override fun hide() = omnibar.hide()

    override fun getText(): String = omnibar.getText()

    override fun hideBackground() {
        val omnibarView = omnibar.omnibarView as? View ?: return
        applyOnLayout(omnibarView) {
            makeOmnibarTransparent(omnibarView)
            hideOmnibarContent(omnibarView)
            showDuckAiTitle(omnibarView)
        }
    }

    override fun showTransparentOmnibar() {
        val omnibarView = omnibar.omnibarView as? View ?: return
        if (rootView.findViewById<View?>(R.id.inputModeWidget) == null) return
        omnibar.show()
        omnibar.isScrollingEnabled = false
        omnibar.setExpanded(true)
        applyOnLayout(omnibarView) {
            makeOmnibarTransparent(omnibarView)
            hideOmnibarContent(omnibarView)
            omnibarView.findViewById<View?>(R.id.duckAIHeader)?.gone()
            omnibarView.findViewById<View?>(R.id.endIconsContainer)?.gone()
            omnibarView.findViewById<View?>(R.id.duckAiSidebar)?.gone()
        }
    }

    private fun makeOmnibarTransparent(omnibarView: View) {
        omnibarView.findViewById<View?>(R.id.toolbarContainer)?.setBackgroundColor(Color.TRANSPARENT)
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainerShadow)?.apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            cardElevation = 0f
            removeCustomShadow()
        }
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainer)?.setCardBackgroundColor(Color.TRANSPARENT)
    }

    private fun View.removeCustomShadow() {
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = 0f
        if (Build.VERSION.SDK_INT >= 28) {
            outlineAmbientShadowColor = Color.BLACK
            outlineSpotShadowColor = Color.BLACK
        }
    }

    private fun hideOmnibarContent(omnibarView: View) {
        omnibarView.findViewById<View?>(R.id.omnibarIconContainer)?.gone()
        omnibarView.findViewById<View?>(R.id.shieldIcon)?.gone()
        omnibarView.findViewById<View?>(R.id.omnibarTextInput)?.gone()
        omnibarView.findViewById<View?>(R.id.pageLoadingIndicator)?.gone()
    }

    private var currentTier: DuckAiTier = DuckAiTier.Unknown
    private var currentUpgradeClick: (() -> Unit)? = null

    private fun showDuckAiTitle(omnibarView: View) {
        val header = omnibarView.findViewById<android.widget.LinearLayout?>(R.id.duckAIHeader)
        val aiTitle = omnibarView.findViewById<TextView?>(R.id.aiTitle)
        omnibarView.findViewById<View?>(R.id.aiIcon)?.gone()
        header?.show()
        header?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        header?.setBackgroundColor(Color.TRANSPARENT)
        aiTitle?.show()
        aiTitle?.textSize = 16f
        applyTierText(aiTitle)
    }

    override fun updateTierTitle(tier: DuckAiTier, onUpgradeClicked: () -> Unit) {
        currentTier = tier
        currentUpgradeClick = onUpgradeClicked
        val omnibarView = omnibar.omnibarView as? View ?: return
        val aiTitle = omnibarView.findViewById<TextView?>(R.id.aiTitle)
        applyTierText(aiTitle)
    }

    private fun applyTierText(aiTitle: TextView?) {
        aiTitle ?: return
        when (currentTier) {
            is DuckAiTier.Free -> {
                val context = aiTitle.context
                val freePlan = context.getString(R.string.duckAiHeaderFreePlan)
                val upgrade = context.getString(R.string.duckAiHeaderUpgrade)
                val full = "$freePlan · $upgrade ↑"
                val spannable = SpannableStringBuilder(full)
                val upgradeStart = full.indexOf(upgrade)
                val upgradeEnd = full.length
                val accentColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            currentUpgradeClick?.invoke()
                        }
                        override fun updateDrawState(ds: TextPaint) {
                            ds.isUnderlineText = false
                        }
                    },
                    upgradeStart,
                    upgradeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                spannable.setSpan(
                    ForegroundColorSpan(accentColor),
                    upgradeStart,
                    upgradeEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                aiTitle.text = spannable
                aiTitle.movementMethod = LinkMovementMethod.getInstance()
                aiTitle.highlightColor = Color.TRANSPARENT
            }
            is DuckAiTier.Paid -> {
                aiTitle.text = aiTitle.context.getString(R.string.duckAiHeaderPaidTitle)
                aiTitle.movementMethod = null
            }
            is DuckAiTier.Unknown -> {
                aiTitle.text = aiTitle.context.getString(R.string.duckAiHeaderPaidTitle)
                aiTitle.movementMethod = null
            }
        }
    }

    private fun applyOnLayout(omnibarView: View, block: () -> Unit) {
        removeLayoutListener(omnibarView)
        block()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (rootView.findViewById<View?>(R.id.inputModeWidget) != null) block()
        }
        layoutListener = listener
        omnibarView.addOnLayoutChangeListener(listener)
    }

    private fun removeLayoutListener(omnibarView: View) {
        layoutListener?.let { omnibarView.removeOnLayoutChangeListener(it) }
        layoutListener = null
    }

    override fun getCardView(): View? {
        val omnibarView = omnibar.omnibarView as? View ?: return null
        return omnibarView.findViewById(R.id.omniBarContainerShadow)
    }

    override fun getButtonsWidth(): Int {
        val omnibarView = omnibar.omnibarView as? View ?: return 0
        val tabsMenu = omnibarView.findViewById<View?>(R.id.tabsMenu)
        val browserMenu = omnibarView.findViewById<View?>(R.id.browserMenu)
        return (tabsMenu?.width ?: 0) + (browserMenu?.width ?: 0)
    }

    override fun restore() {
        currentTier = DuckAiTier.Unknown
        currentUpgradeClick = null
        (omnibar.omnibarView as? View)?.let { removeLayoutListener(it) }
        restoreOmnibarColors()
        restoreOmnibarContent()
        restoreBottomOmnibarPosition()
        if (omnibar.omnibarType == OmnibarType.SPLIT) {
            rootView.findViewById<View?>(R.id.navigationBar)?.show()
        }
        omnibar.isScrollingEnabled = true
    }

    private fun restoreOmnibarContent() {
        val omnibarView = omnibar.omnibarView as? View ?: return
        omnibarView.findViewById<View?>(R.id.endIconsContainer)?.show()
        omnibarView.findViewById<View?>(R.id.omnibarIconContainer)?.show()
        omnibarView.findViewById<View?>(R.id.omnibarTextInput)?.show()
        omnibarView.findViewById<View?>(R.id.duckAIHeader)?.gone()
    }

    private fun restoreOmnibarColors() {
        val omnibarView = omnibar.omnibarView as? View ?: return
        val ctx = omnibarView.context
        omnibarView.findViewById<View?>(R.id.toolbarContainer)
            ?.setBackgroundColor(ctx.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorToolbar))
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainerShadow)?.apply {
            setCardBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
            val isTop = omnibar.omnibarType == OmnibarType.SINGLE_TOP || omnibar.omnibarType == OmnibarType.SPLIT
            cardElevation = if (isTop) 6f.toPx() else 3f.toPx()
        }
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainer)
            ?.setCardBackgroundColor(ctx.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorWindow))
    }

    private fun restoreBottomOmnibarPosition() {
        if (omnibar.omnibarType != OmnibarType.SINGLE_BOTTOM) return
        val omnibarView = omnibar.omnibarView as? View ?: return
        val params = omnibarView.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        if (params.gravity != Gravity.TOP) return

        omnibarView.updateLayoutParams<CoordinatorLayout.LayoutParams> { gravity = Gravity.BOTTOM }
        val parent = omnibarView.parent as? ViewGroup
        parent?.removeView(omnibarView)
        parent?.addView(omnibarView)
        omnibarView.elevation = 0f
        rootView.findViewById<View?>(R.id.browserLayout)?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = BottomOmnibarBrowserContainerLayoutBehavior()
        }
    }

    override fun forceToTop() {
        val omnibarView = omnibar.omnibarView as? View ?: return
        val parent = omnibarView.parent as? ViewGroup ?: return

        if (omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM) {
            omnibarView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                gravity = Gravity.TOP
            }
            parent.removeView(omnibarView)
            parent.addView(omnibarView, 0)
            omnibarView.elevation = 1f.toPx()

            val topBehavior = TopOmnibarBrowserContainerLayoutBehavior(rootView.context, null)
            rootView.findViewById<View?>(R.id.browserLayout)?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                behavior = topBehavior
            }
        }
        omnibar.isScrollingEnabled = false
        omnibar.setExpanded(true)
    }
}
