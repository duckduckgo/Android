package com.duckduckgo.common.utils.extensions

import android.util.DisplayMetrics
import android.util.TypedValue

fun Int.dpToPx(displayMetrics: DisplayMetrics) = dpToFloat(displayMetrics).toInt()

fun Int.dpToFloat(displayMetrics: DisplayMetrics) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP,
    this.toFloat(),
    displayMetrics,
)
