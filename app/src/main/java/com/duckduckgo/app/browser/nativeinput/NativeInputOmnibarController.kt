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
}

interface NativeInputOmnibarController : OmnibarState {
    fun show()
    fun hide()
    fun getText(): String
    fun hideBackground()
    fun restore()
    fun forceToTop()
}

class RealNativeInputOmnibarController(
    private val omnibar: Omnibar,
    private val rootView: ViewGroup,
) : NativeInputOmnibarController {

    override fun isDuckAiMode(): Boolean = omnibar.viewMode == DuckAI

    override fun isBrowserMode(): Boolean = omnibar.viewMode is Omnibar.ViewMode.Browser

    override fun isOmnibarBottom(): Boolean =
        omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM

    override fun show() = omnibar.show()

    override fun hide() = omnibar.hide()

    override fun getText(): String = omnibar.getText()

    override fun hideBackground() {
        val omnibarView = omnibar.omnibarView as? View ?: return

        val toolbarContainer = omnibarView.findViewById<View?>(R.id.toolbarContainer)
        val cardShadow = omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainerShadow)
        val innerCard = omnibarView.findViewById<MaterialCardView?>(R.id.omniBarContainer)
        val header = omnibarView.findViewById<android.widget.LinearLayout?>(R.id.duckAIHeader)
        val aiIcon = omnibarView.findViewById<View?>(R.id.aiIcon)
        val aiTitle = omnibarView.findViewById<TextView?>(R.id.aiTitle)
        val leadingIconContainer = omnibarView.findViewById<View?>(R.id.omnibarIconContainer)
        val shieldIcon = omnibarView.findViewById<View?>(R.id.shieldIcon)
        val omnibarTextInput = omnibarView.findViewById<View?>(R.id.omnibarTextInput)
        val pageLoadingIndicator = omnibarView.findViewById<View?>(R.id.pageLoadingIndicator)
        val fireIconMenu = omnibarView.findViewById<View?>(R.id.fireIconMenu)
        val tabsMenu = omnibarView.findViewById<View?>(R.id.tabsMenu)
        val browserMenu = omnibarView.findViewById<View?>(R.id.browserMenu)

        fun apply() {
            if (rootView.findViewById<View?>(R.id.inputModeWidget) == null) return
            toolbarContainer?.setBackgroundColor(Color.TRANSPARENT)
            cardShadow?.setCardBackgroundColor(Color.TRANSPARENT)
            cardShadow?.cardElevation = 0f
            innerCard?.setCardBackgroundColor(Color.TRANSPARENT)
            leadingIconContainer?.gone()
            shieldIcon?.gone()
            omnibarTextInput?.gone()
            pageLoadingIndicator?.gone()
            fireIconMenu?.show()
            tabsMenu?.show()
            browserMenu?.show()
            header?.show()
            header?.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            header?.setBackgroundColor(Color.TRANSPARENT)
            aiIcon?.gone()
            aiTitle?.show()
            aiTitle?.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline6)
        }

        apply()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> apply() }
        omnibarView.addOnLayoutChangeListener(listener)
        omnibarView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    omnibarView.removeOnLayoutChangeListener(listener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    override fun restore() {
        val omnibarView = omnibar.omnibarView as? View
        if (omnibarView != null) {
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

        if (omnibar.omnibarType == OmnibarType.SINGLE_BOTTOM) {
            val params = omnibarView?.layoutParams as? CoordinatorLayout.LayoutParams
            if (params?.gravity == Gravity.TOP) {
                omnibarView.updateLayoutParams<CoordinatorLayout.LayoutParams> { gravity = Gravity.BOTTOM }
                val parent = omnibarView.parent as? ViewGroup
                parent?.removeView(omnibarView)
                parent?.addView(omnibarView)
                omnibarView.elevation = 0f
                rootView.findViewById<View?>(R.id.browserLayout)?.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                    behavior = BottomOmnibarBrowserContainerLayoutBehavior()
                }
            }
        }

        if (omnibar.omnibarType == OmnibarType.SPLIT) {
            rootView.findViewById<View?>(R.id.navigationBar)?.show()
        }

        omnibar.isScrollingEnabled = true
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
