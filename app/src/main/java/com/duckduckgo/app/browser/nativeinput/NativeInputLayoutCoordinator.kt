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

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.duckduckgo.app.browser.R
import com.google.android.material.card.MaterialCardView

class NativeInputLayoutCoordinator(
    private val rootView: ViewGroup,
    private val omnibarState: OmnibarState,
) {
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun View.snapshotPadding() = Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    fun buildWidgetLayoutParams(isBottom: Boolean): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isBottom) Gravity.BOTTOM else Gravity.TOP
        }
    }

    fun applyBottomCardCorners(widgetView: View, isBottom: Boolean) {
        if (!isBottom) return
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val radius = card.resources.getDimension(R.dimen.extraLargeShapeCornerRadius)
        card.shapeAppearanceModel =
            card.shapeAppearanceModel
                .toBuilder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .setBottomLeftCornerSize(0f)
                .setBottomRightCornerSize(0f)
                .build()
    }

    fun applyBottomCardShape(widgetView: View, isBottom: Boolean) {
        if (!isBottom) return
        applyBottomCardCorners(widgetView, isBottom)
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.marginStart = 0
        params.marginEnd = 0
        params.bottomMargin = 0
        card.layoutParams = params
    }

    fun configureAutocompleteLayout(widgetView: View, isBottom: Boolean) {
        val autoCompleteList = rootView.findViewById<View?>(R.id.autoCompleteSuggestionsList) ?: return
        val focusedView = rootView.findViewById<View?>(R.id.focusedView)
        val baseElevation = maxOf(autoCompleteList.elevation, focusedView?.elevation ?: 0f)
        val targetElevation = baseElevation + widgetView.resources.displayMetrics.density
        widgetView.elevation = maxOf(widgetView.elevation, targetElevation)
        widgetView.bringToFront()

        val targets =
            buildList {
                add(autoCompleteList to autoCompleteList.snapshotPadding())
                focusedView?.let { add(it to it.snapshotPadding()) }
            }
        fun applyPadding(deltaTop: Int, deltaBottom: Int) {
            targets.forEach { (view, padding) ->
                val newTop = padding.top + deltaTop
                val newBottom = padding.bottom + deltaBottom
                // Only post requestLayout when padding actually changed to avoid extra layout passes in steady state
                if (view.paddingTop != newTop || view.paddingBottom != newBottom) {
                    view.setPadding(padding.left, newTop, padding.right, newBottom)
                    // Force RecyclerView to reposition items after padding changes during widget enter animation
                    view.post { view.requestLayout() }
                }
            }
        }

        fun applyForWidgetPosition() {
            val topOffset = if (isBottom) 0 else maxOf(0, widgetView.bottom - autoCompleteList.top)
            val bottomOffset = if (isBottom) maxOf(0, autoCompleteList.bottom - widgetView.top) else 0
            applyPadding(deltaTop = topOffset, deltaBottom = bottomOffset)
        }

        widgetView.post { applyForWidgetPosition() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyForWidgetPosition()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        autoCompleteList.addOnLayoutChangeListener(layoutListener)
        focusedView?.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    applyPadding(deltaTop = 0, deltaBottom = 0)
                    v.removeOnLayoutChangeListener(layoutListener)
                    autoCompleteList.removeOnLayoutChangeListener(layoutListener)
                    focusedView?.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    fun configureContentOffset(widgetView: View, isBottom: Boolean) {
        data class Target(val view: View, val basePadding: Padding)
        val newTabContent =
            rootView.findViewById<View?>(R.id.newTabPage)
                ?: rootView.findViewById(R.id.includeNewBrowserTab)
        val targets =
            listOfNotNull(
                rootView.findViewById(R.id.browserLayout),
                newTabContent,
            ).map { Target(it, it.snapshotPadding()) }
        if (targets.isEmpty()) return
        val anchor = widgetView.findViewById(R.id.inputModeWidgetCard) ?: widgetView

        val overlap = widgetView.resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_5)

        fun applyPadding(view: View, padding: Padding, deltaTop: Int, deltaBottom: Int) {
            view.setPadding(
                padding.left,
                padding.top + deltaTop,
                padding.right,
                padding.bottom + deltaBottom,
            )
        }

        fun isLogoVisible(view: View): Boolean {
            return view == newTabContent &&
                rootView.findViewById<View?>(R.id.ddgLogo)?.visibility == View.VISIBLE
        }

        fun computeDeltaTop(view: View, anchorBottomInWindow: Int): Int {
            if (isBottom || isLogoVisible(view)) return 0
            val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
            return maxOf(0, anchorBottomInWindow - viewLocation[1])
        }

        fun computeDeltaBottom(): Int {
            if (!isBottom) return 0
            return if (omnibarState.isOmnibarBottom()) {
                maxOf(0, overlap)
            } else {
                maxOf(0, anchor.height - overlap)
            }
        }

        fun applyOffset() {
            if (!widgetView.isShown) {
                targets.forEach { applyPadding(it.view, it.basePadding, deltaTop = 0, deltaBottom = 0) }
                return
            }
            val anchorLocation = IntArray(2).also { anchor.getLocationInWindow(it) }
            val anchorBottomInWindow = anchorLocation[1] + anchor.height
            val deltaBottom = computeDeltaBottom()
            targets.forEach { target ->
                val deltaTop = computeDeltaTop(target.view, anchorBottomInWindow)
                applyPadding(target.view, target.basePadding, deltaTop, deltaBottom)
            }
        }

        widgetView.post { applyOffset() }
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyOffset()
            }
        widgetView.addOnLayoutChangeListener(layoutListener)
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    targets.forEach { target ->
                        applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                    }
                    v.removeOnLayoutChangeListener(layoutListener)
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }

    fun applyForcedBottomTranslation(widgetView: View, isBottom: Boolean) {
        val shouldForce = isBottom && !omnibarState.isOmnibarBottom()
        if (!shouldForce) {
            widgetView.translationY = 0f
            return
        }
        fun applyOffset() {
            val gap = maxOf(0, rootView.height - widgetView.bottom)
            if (widgetView.translationY != gap.toFloat()) {
                widgetView.translationY = gap.toFloat()
            }
        }
        applyOffset()
        val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                applyOffset()
            }
        rootView.addOnLayoutChangeListener(layoutListener)
        widgetView.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    rootView.removeOnLayoutChangeListener(layoutListener)
                    v.removeOnAttachStateChangeListener(this)
                }
            },
        )
    }
}
