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

package com.duckduckgo.nextsteps.impl.ui

import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import com.duckduckgo.common.ui.isRebrandAreaEnabled
import com.duckduckgo.common.ui.view.applyRmfCardStyle
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.getDimensionFromAttr
import com.duckduckgo.nextsteps.impl.R
import com.duckduckgo.nextsteps.impl.databinding.ViewNextStepsCardBinding
import com.duckduckgo.mobile.android.R as CommonR

internal val ViewNextStepsCardBinding.action: DaxButton
    get() = root.findViewById(R.id.primaryAction)

internal fun ViewNextStepsCardBinding.applyRmfStyle() {
    if (!root.context.isRebrandAreaEnabled(CommonR.attr.daxRebrandRmfCards)) return
    root.applyRmfCardStyle(closeButton)
    root.radius = root.context.getDimensionFromAttr(CommonR.attr.daxContainerRadius, CommonR.dimen.extraLargeShapeCornerRadius)
    if (root.resources.configuration.smallestScreenWidthDp >= 600) {
        itemImage.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topToBottom = ConstraintLayout.LayoutParams.UNSET
            topToTop = sectionTitle.id
            bottomToBottom = itemDescription.id
            verticalBias = 0.5f
        }
    }
    val old = primaryAction
    val parent = old.parent as ViewGroup
    val button = DaxButtonPrimary(ContextThemeWrapper(root.context, CommonR.style.ThemeOverlay_Rebrand), null).apply {
        id = old.id
        setSingleLine(false)
        maxLines = Int.MAX_VALUE
        ellipsize = null
    }
    val index = parent.indexOfChild(old)
    parent.removeView(old)
    parent.addView(button, index, old.layoutParams)
    button.updateLayoutParams<ConstraintLayout.LayoutParams> {
        width = 0
        constrainedWidth = false
        matchConstraintDefaultWidth = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        topMargin = root.resources.getDimensionPixelSize(CommonR.dimen.keyline_3)
    }
    val content = root.getChildAt(0) as ViewGroup
    val padding = root.resources.getDimensionPixelSize(CommonR.dimen.keyline_5)
    content.setPadding(padding, padding, padding, padding - button.insetBottom)
}
