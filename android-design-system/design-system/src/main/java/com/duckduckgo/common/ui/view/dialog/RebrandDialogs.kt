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

package com.duckduckgo.common.ui.view.dialog

import android.content.Context
import android.widget.ImageView
import com.duckduckgo.mobile.android.R

internal fun Context.rebrandDialogCardWidth(): Int {
    val maxWidth = resources.getDimensionPixelSize(R.dimen.rebrandDialogMaxWidth)
    val margin = resources.getDimensionPixelSize(R.dimen.keyline_5)
    return minOf(maxWidth, resources.displayMetrics.widthPixels - margin * 2)
}

internal fun ImageView.applyRebrandHeaderIconStyle() {
    val inset = resources.getDimensionPixelSize(R.dimen.rebrandDialogIconInset)
    setBackgroundResource(R.drawable.background_dialog_icon_circular)
    setPadding(inset, inset, inset, inset)
}
