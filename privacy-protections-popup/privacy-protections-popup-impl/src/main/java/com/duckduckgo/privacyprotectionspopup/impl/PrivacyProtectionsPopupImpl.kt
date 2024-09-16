/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import android.content.Context
import android.graphics.Point
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.PopupWindow
import androidx.core.view.doOnDetach
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.duckduckgo.common.ui.view.shape.DaxBubbleEdgeTreatment
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopup
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DONT_SHOW_AGAIN_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.PRIVACY_DASHBOARD_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.privacyprotectionspopup.impl.R.*
import com.duckduckgo.privacyprotectionspopup.impl.databinding.PopupButtonsHorizontalBinding
import com.duckduckgo.privacyprotectionspopup.impl.databinding.PopupButtonsVerticalBinding
import com.duckduckgo.privacyprotectionspopup.impl.databinding.PopupPrivacyDashboardBinding
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PrivacyProtectionsPopupImpl(
    private val anchor: View,
) : PrivacyProtectionsPopup {

    private val context: Context get() = anchor.context
    private val _events = MutableSharedFlow<PrivacyProtectionsPopupUiEvent>(extraBufferCapacity = 1)
    private var state: PrivacyProtectionsPopupViewState = PrivacyProtectionsPopupViewState.Gone
    private var popupWindow: PopupWindow? = null

    override val events: Flow<PrivacyProtectionsPopupUiEvent> = _events.asSharedFlow()

    override fun setViewState(viewState: PrivacyProtectionsPopupViewState) {
        if (viewState != state) {
            state = viewState

            when (viewState) {
                is PrivacyProtectionsPopupViewState.Visible -> {
                    showPopup(viewState)
                }

                PrivacyProtectionsPopupViewState.Gone -> {
                    dismissPopup()
                }
            }
        }
    }

    override fun onConfigurationChanged() {
        when (val state = state) {
            is PrivacyProtectionsPopupViewState.Visible -> {
                dismissPopup()
                showPopup(state)
            }

            PrivacyProtectionsPopupViewState.Gone -> {
                // no-op
            }
        }
    }

    private fun showPopup(viewState: PrivacyProtectionsPopupViewState.Visible) = anchor.doOnLayout {
        val popupContent = createPopupContentView(viewState.doNotShowAgainOptionAvailable)
        val popupWindowSpec = createPopupWindowSpec(popupContent = popupContent.root)

        popupWindowSpec.overrideContentPaddingStartPx?.let { contentPaddingStartPx ->
            popupContent.root.updatePaddingRelative(start = contentPaddingStartPx)
        }

        popupContent.buttons.dismiss.setOnClickListener { _events.tryEmit(DISMISS_CLICKED) }
        popupContent.buttons.doNotShowAgain.setOnClickListener { _events.tryEmit(DONT_SHOW_AGAIN_CLICKED) }
        popupContent.buttons.disableProtections.setOnClickListener { _events.tryEmit(DISABLE_PROTECTIONS_CLICKED) }
        popupContent.anchorOverlay.setOnClickListener {
            anchor.performClick()
            _events.tryEmit(PRIVACY_DASHBOARD_CLICKED)
        }
        popupContent.omnibarOverlay.setOnClickListener { _events.tryEmit(DISMISSED) }

        popupContent.anchorOverlay.layoutParams = popupContent.anchorOverlay.layoutParams.apply {
            height = anchor.measuredHeight
            if (this is MarginLayoutParams) {
                marginStart = anchor.locationInWindow.x - popupContent.root.paddingStart
            }
        }

        popupWindow = PopupWindow(
            popupContent.root,
            popupWindowSpec.width,
            LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setOnDismissListener {
                _events.tryEmit(DISMISSED)
                popupWindow = null
            }
            showAsDropDown(anchor, popupWindowSpec.horizontalOffsetPx, popupWindowSpec.verticalOffsetPx)
        }

        anchor.doOnDetach { dismissPopup() }
    }

    private fun dismissPopup() {
        popupWindow?.setOnDismissListener(null)
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun createPopupContentView(doNotShowAgainAvailable: Boolean): PopupViewHolder {
        val popupContent = PopupPrivacyDashboardBinding.inflate(LayoutInflater.from(context))
        val buttonsViewHolder = inflateButtons(popupContent, doNotShowAgainAvailable)
        adjustBodyTextToAvailableWidth(popupContent)

        // Override CardView's default elevation with popup/dialog elevation
        popupContent.cardView.cardElevation = POPUP_DEFAULT_ELEVATION_DP.toPx()

        val cornerRadius = context.resources.getDimension(R.dimen.mediumShapeCornerRadius)
        val cornerSize = context.resources.getDimension(R.dimen.daxBubbleDialogEdge)
        val distanceFromEdge = EDGE_TREATMENT_DISTANCE_FROM_EDGE.toPx() - POPUP_HORIZONTAL_OFFSET_DP.toPx()
        val edgeTreatment = DaxBubbleEdgeTreatment(cornerSize, distanceFromEdge)

        popupContent.cardView.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .setTopEdge(edgeTreatment)
            .build()

        popupContent.shieldIconHighlight.startAnimation(buildShieldIconHighlightAnimation())

        return PopupViewHolder(
            root = popupContent.root,
            anchorOverlay = popupContent.anchorOverlay,
            omnibarOverlay = popupContent.omnibarOverlay,
            buttons = buttonsViewHolder,
        )
    }

    private fun inflateButtons(popupContent: PopupPrivacyDashboardBinding, doNotShowAgainAvailable: Boolean): PopupButtonsViewHolder {
        val availableWidth = getAvailablePopupCardViewContentWidthPx(popupContent)

        val horizontalButtons = PopupButtonsHorizontalBinding
            .inflate(LayoutInflater.from(context), popupContent.buttonsContainer, false)
            .apply {
                dontShowAgainButton.isVisible = doNotShowAgainAvailable
                dismissButton.isVisible = !doNotShowAgainAvailable
            }

        val horizontalButtonsWidth = horizontalButtons.root
            .apply { measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED) }
            .measuredWidth

        return if (horizontalButtonsWidth <= availableWidth) {
            popupContent.buttonsContainer.addView(horizontalButtons.root)
            PopupButtonsViewHolder(
                dismiss = horizontalButtons.dismissButton,
                doNotShowAgain = horizontalButtons.dontShowAgainButton,
                disableProtections = horizontalButtons.disableButton,
            )
        } else {
            val verticalButtons = PopupButtonsVerticalBinding
                .inflate(LayoutInflater.from(context), popupContent.buttonsContainer, true)
                .apply {
                    dontShowAgainButton.isVisible = doNotShowAgainAvailable
                    dismissButton.isVisible = !doNotShowAgainAvailable
                }
            popupContent.buttonsContainer.layoutParams = popupContent.buttonsContainer.layoutParams.apply { width = 0 }
            PopupButtonsViewHolder(
                dismiss = verticalButtons.dismissButton,
                doNotShowAgain = verticalButtons.dontShowAgainButton,
                disableProtections = verticalButtons.disableButton,
            )
        }
    }

    private fun adjustBodyTextToAvailableWidth(popupContent: PopupPrivacyDashboardBinding) {
        val availableWidth = getAvailablePopupCardViewContentWidthPx(popupContent)

        val defaultText = context.getString(string.privacy_protections_popup_body)
        val shortText = context.getString(string.privacy_protections_popup_body_short)

        popupContent.bodyText.post {
            val textPaint = popupContent.bodyText.paint

            popupContent.bodyText.text = when {
                textPaint.measureText(defaultText) <= availableWidth -> defaultText
                textPaint.measureText(shortText) <= availableWidth -> shortText
                else -> defaultText // No need to use the shorter text if it wraps anyway
            }
        }
    }

    private fun createPopupWindowSpec(popupContent: View): PopupWindowSpec {
        val distanceFromStartEdgeOfTheScreenPx = anchor.locationInWindow.x + POPUP_HORIZONTAL_OFFSET_DP.toPx()

        val overrideContentPaddingStartPx = if (distanceFromStartEdgeOfTheScreenPx - popupContent.paddingStart < 0) {
            distanceFromStartEdgeOfTheScreenPx
        } else {
            null
        }

        // Adjust anchor position for extra margin that CardView needs in order draw its shadow
        val horizontalOffsetPx = POPUP_HORIZONTAL_OFFSET_DP.toPx() - popupContent.paddingStart

        // Align top of the popup layout with the top of the anchor
        val verticalOffsetPx = -anchor.measuredHeight - popupContent.paddingTop

        // Calculate width because PopupWindow doesn't handle WRAP_CONTENT as expected
        val popupContentWidth = popupContent
            .apply { measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED) }
            .measuredWidth

        // If we reduce the start padding, then the max width is increased so that paddings appear symmetrical
        val maxPopupWindowWidth = if (overrideContentPaddingStartPx == null) {
            context.screenWidth
        } else {
            context.screenWidth + popupContent.paddingEnd - overrideContentPaddingStartPx
        }

        // Stretch the popup to the entire width when the screen is small
        val popupWidth = if (popupContentWidth > 0.7 * maxPopupWindowWidth) {
            maxPopupWindowWidth
        } else {
            popupContentWidth
        }

        return PopupWindowSpec(
            width = popupWidth,
            horizontalOffsetPx = horizontalOffsetPx,
            verticalOffsetPx = verticalOffsetPx,
            overrideContentPaddingStartPx = overrideContentPaddingStartPx,
        )
    }

    private fun getAvailablePopupCardViewContentWidthPx(popupContent: PopupPrivacyDashboardBinding): Int {
        val popupExternalMarginsWidth = 2 * anchor.locationInWindow.x + POPUP_HORIZONTAL_OFFSET_DP.toPx()
        val popupInternalPaddingWidth = popupContent.cardViewContent.paddingStart + popupContent.cardViewContent.paddingEnd
        return context.screenWidth - popupExternalMarginsWidth - popupInternalPaddingWidth
    }

    private class PopupViewHolder(
        val root: View,
        val anchorOverlay: View,
        val omnibarOverlay: View,
        val buttons: PopupButtonsViewHolder,
    )

    private class PopupButtonsViewHolder(
        val dismiss: Button,
        val doNotShowAgain: Button,
        val disableProtections: Button,
    )

    private data class PopupWindowSpec(
        val width: Int,
        val horizontalOffsetPx: Int,
        val verticalOffsetPx: Int,
        val overrideContentPaddingStartPx: Int?,
    )

    private companion object {
        const val POPUP_DEFAULT_ELEVATION_DP = 8f
        const val EDGE_TREATMENT_DISTANCE_FROM_EDGE = 10f

        // Alignment of popup left edge vs. anchor left edge
        const val POPUP_HORIZONTAL_OFFSET_DP = -4
    }
}

private val View.locationInWindow: Point
    get() {
        val location = IntArray(2)
        getLocationInWindow(location)
        return Point(location[0], location[1])
    }

private val Context.screenWidth: Int
    get() = resources.displayMetrics.widthPixels
