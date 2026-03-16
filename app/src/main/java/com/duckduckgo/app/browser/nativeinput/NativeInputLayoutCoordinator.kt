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
    fun isWidgetBottom(): Boolean = omnibarState.isDuckAiMode() || omnibarState.isOmnibarBottom()

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun View.snapshotPadding() = Padding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    fun buildWidgetLayoutParams(): ViewGroup.LayoutParams {
        return CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = if (isWidgetBottom()) Gravity.BOTTOM else Gravity.TOP
        }
    }

    fun applyBottomCardShape(widgetView: View) {
        if (!isWidgetBottom()) return
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
        card.useCompatPadding = false
    }

    fun applyRoundedCardShape(widgetView: View) {
        val card = widgetView.findViewById<MaterialCardView?>(R.id.inputModeWidgetCard) ?: return
        val radius = card.resources.getDimension(R.dimen.extraLargeShapeCornerRadius)
        card.shapeAppearanceModel =
            card.shapeAppearanceModel
                .toBuilder()
                .setAllCornerSizes(radius)
                .build()
        card.useCompatPadding = true
    }

    fun configureAutocompleteLayout(widgetView: View) {
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
                view.setPadding(
                    padding.left,
                    padding.top + deltaTop,
                    padding.right,
                    padding.bottom + deltaBottom,
                )
            }
        }

        fun applyForWidgetPosition() {
            val isBottom = isWidgetBottom()
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

    fun configureContentOffset(widgetView: View) {
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

        fun applyOffset() {
            if (!widgetView.isShown) {
                targets.forEach { target ->
                    applyPadding(target.view, target.basePadding, deltaTop = 0, deltaBottom = 0)
                }
                return
            }
            val isBottom = isWidgetBottom()
            val anchorLocation = IntArray(2).also { anchor.getLocationInWindow(it) }
            val anchorBottomInWindow = anchorLocation[1] + anchor.height
            targets.forEach { target ->
                val view = target.view
                val viewLocation = IntArray(2).also { view.getLocationInWindow(it) }
                val deltaTop = if (isBottom) 0 else maxOf(0, anchorBottomInWindow - viewLocation[1])
                val deltaBottom =
                    if (isBottom) {
                        if (omnibarState.isOmnibarBottom()) {
                            maxOf(0, overlap)
                        } else {
                            maxOf(0, anchor.height - overlap)
                        }
                    } else {
                        0
                    }
                applyPadding(view, target.basePadding, deltaTop, deltaBottom)
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

    fun applyForcedBottomTranslation(widgetView: View) {
        val shouldForce = isWidgetBottom() && !omnibarState.isOmnibarBottom()
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
