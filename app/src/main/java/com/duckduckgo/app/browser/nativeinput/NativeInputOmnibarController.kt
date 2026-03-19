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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    fun showTabsAndMenuButtons()
    fun getButtonsWidth(): Int
    fun restore()
    fun forceToTop()
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
            omnibarView.findViewById<View?>(R.id.fireIconMenu)?.show()
            omnibarView.findViewById<View?>(R.id.tabsMenu)?.show()
            omnibarView.findViewById<View?>(R.id.browserMenu)?.show()
        }
    }

    override fun showTabsAndMenuButtons() {
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
            omnibarView.findViewById<View?>(R.id.fireIconMenu)?.gone()
            omnibarView.findViewById<View?>(R.id.tabsMenu)?.show()
            omnibarView.findViewById<View?>(R.id.browserMenu)?.show()
        }
    }

    private fun makeOmnibarTransparent(omnibarView: View) {
        omnibarView.findViewById<View?>(R.id.toolbarContainer)?.setBackgroundColor(Color.TRANSPARENT)
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainerShadow)?.apply {
            setCardBackgroundColor(Color.TRANSPARENT)
            cardElevation = 0f
        }
        omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainer)?.setCardBackgroundColor(Color.TRANSPARENT)
    }

    private fun hideOmnibarContent(omnibarView: View) {
        omnibarView.findViewById<View?>(R.id.omnibarIconContainer)?.gone()
        omnibarView.findViewById<View?>(R.id.shieldIcon)?.gone()
        omnibarView.findViewById<View?>(R.id.omnibarTextInput)?.gone()
        omnibarView.findViewById<View?>(R.id.pageLoadingIndicator)?.gone()
    }

    private fun showDuckAiTitle(omnibarView: View) {
        val header = omnibarView.findViewById<android.widget.LinearLayout?>(R.id.duckAIHeader)
        val aiTitle = omnibarView.findViewById<TextView?>(R.id.aiTitle)
        omnibarView.findViewById<View?>(R.id.aiIcon)?.gone()
        header?.show()
        header?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        header?.setBackgroundColor(Color.TRANSPARENT)
        aiTitle?.show()
        aiTitle?.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6)
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

    override fun getButtonsWidth(): Int {
        val omnibarView = omnibar.omnibarView as? View ?: return 0
        val tabsMenu = omnibarView.findViewById<View?>(R.id.tabsMenu)
        val browserMenu = omnibarView.findViewById<View?>(R.id.browserMenu)
        return (tabsMenu?.width ?: 0) + (browserMenu?.width ?: 0)
    }

    override fun restore() {
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
            cardElevation = 1f.toPx()
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
