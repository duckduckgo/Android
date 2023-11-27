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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.PopupWindow
import androidx.core.view.doOnDetach
import androidx.core.view.doOnLayout
import androidx.core.view.updatePaddingRelative
import com.duckduckgo.common.ui.view.shape.DaxBubbleEdgeTreatment
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopup
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
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
    private var visible = false
    private var popupWindow: PopupWindow? = null

    override fun setViewState(viewState: PrivacyProtectionsPopupViewState) {
        if (viewState.visible != visible) {
            visible = viewState.visible

            if (visible) {
                showPopup()
            } else {
                dismissPopup()
            }
        }
    }

    override val events: Flow<PrivacyProtectionsPopupUiEvent> = _events.asSharedFlow()

    private fun showPopup() = anchor.doOnLayout {
        val popupContent = createPopupContentView()
        val popupWindowSpec = createPopupWindowSpec(popupContent = popupContent.root)

        popupWindowSpec.overrideContentPaddingStartPx?.let { contentPaddingStartPx ->
            popupContent.root.updatePaddingRelative(start = contentPaddingStartPx)
        }

        popupContent.dismissButton.setOnClickListener { _events.tryEmit(DISMISS_CLICKED) }
        popupContent.disableButton.setOnClickListener { _events.tryEmit(DISABLE_PROTECTIONS_CLICKED) }

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

    private fun createPopupContentView(): PopupPrivacyDashboardBinding {
        val popupContent = PopupPrivacyDashboardBinding.inflate(LayoutInflater.from(context))

        // Override CardView's default elevation with popup/dialog elevation
        popupContent.cardView.cardElevation = POPUP_DEFAULT_ELEVATION_DP.toPx()

        val cornderRadius = context.resources.getDimension(R.dimen.mediumShapeCornerRadius)
        val cornerSize = context.resources.getDimension(R.dimen.daxBubbleDialogEdge)
        val distanceFromEdge = EDGE_TREATMENT_DISTANCE_FROM_EDGE.toPx()
        val edgeTreatment = DaxBubbleEdgeTreatment(cornerSize, distanceFromEdge)

        popupContent.cardView.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornderRadius)
            .setTopEdge(edgeTreatment)
            .build()

        return popupContent
    }

    private fun createPopupWindowSpec(popupContent: View): PopupWindowSpec {
        val distanceFromStartEdgeOfTheScreenPx = anchor.xLocationOnScreen

        val overrideContentPaddingStartPx = if (distanceFromStartEdgeOfTheScreenPx - popupContent.paddingStart < 0) {
            distanceFromStartEdgeOfTheScreenPx
        } else {
            null
        }

        // Adjust anchor position for extra margin that CardView needs in order draw its shadow
        val horizontalOffsetPx = -popupContent.paddingStart

        // Adjust anchor position for the CardView's edge treatment (arrow-like shape on top of the card)
        val verticalOffsetPx = POPUP_WINDOW_VERTICAL_OFFSET_DP.toPx().toInt()

        // Calculate width because PopupWindow doesn't handle WRAP_CONTENT as expected
        val popupContentWidth = popupContent
            .apply { measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED) }
            .measuredWidth

        val screenWidth = context.resources.displayMetrics.widthPixels

        // If we reduce the start padding, then the max width is increased so that paddings appear symmetrical
        val maxPopupWindowWidth = if (overrideContentPaddingStartPx == null) {
            screenWidth
        } else {
            screenWidth + popupContent.paddingEnd - overrideContentPaddingStartPx
        }

        val popupWidth = popupContentWidth.coerceAtMost(maxPopupWindowWidth)

        return PopupWindowSpec(
            width = popupWidth,
            horizontalOffsetPx = horizontalOffsetPx,
            verticalOffsetPx = verticalOffsetPx,
            overrideContentPaddingStartPx = overrideContentPaddingStartPx,
        )
    }

    private data class PopupWindowSpec(
        val width: Int,
        val horizontalOffsetPx: Int,
        val verticalOffsetPx: Int,
        val overrideContentPaddingStartPx: Int?,
    )

    private companion object {
        const val POPUP_DEFAULT_ELEVATION_DP = 8f
        const val EDGE_TREATMENT_DISTANCE_FROM_EDGE = 10f
        const val POPUP_WINDOW_VERTICAL_OFFSET_DP = -12f
    }
}

private val View.xLocationOnScreen: Int
    get() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[0]
    }
