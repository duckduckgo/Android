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

package com.duckduckgo.sync.impl.ui.pairing

import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.duckduckgo.mobile.android.R as CommonR

internal fun DaxTextView.showLeadingProgressSpinner() {
    val spec = CircularProgressIndicatorSpec(context, null, 0).apply {
        indicatorSize = 20.toPx()
        indicatorInset = 0
        trackThickness = 3.toPx()
        indicatorColors = intArrayOf(context.getColorFromAttr(CommonR.attr.daxColorAccentBlue))
    }
    val spinner = IndeterminateDrawable.createCircularDrawable(context, spec).apply {
        setVisible(true, false)
    }
    setCompoundDrawablesRelativeWithIntrinsicBounds(spinner, null, null, null)
}
