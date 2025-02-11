/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import androidx.annotation.StringRes
import androidx.core.view.children
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar

/*
 * Common view operations, simplified as Kotlin extensions
 * Based on https://github.com/ravidsrk/kotlinextensions.com
 */

/** Show the view (visibility = View.VISIBLE) */
fun View.show(): View {
    if (visibility != View.VISIBLE) {
        visibility = View.VISIBLE
    }
    return this
}

/** Hide the view. (visibility = View.INVISIBLE) */
fun View.hide(): View {
    if (visibility != View.INVISIBLE) {
        visibility = View.INVISIBLE
    }
    return this
}

/** Remove the view (visibility = View.GONE) */
fun View.gone(): View {
    if (visibility != View.GONE) {
        visibility = View.GONE
    }
    return this
}

/** Extension method to show a keyboard for View. */
@Deprecated(
    "This may be unreliable. Use the showKeyboard() extension function from an Activity or Fragment instead.",
    ReplaceWith("Activity.showKeyboard(editText)"),
)
fun View.showKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    this.requestFocus()
    imm.showSoftInput(this, 0)
}

/**
 * Try to hide the keyboard and returns whether it worked
 * https://stackoverflow.com/questions/1109022/close-hide-the-android-soft-keyboard
 */
@Deprecated(
    "This may be unreliable. Use the hideKeyboard() extension function from an Activity or Fragment instead.",
    ReplaceWith("Activity.hideKeyboard(editText)"),
)
fun View.hideKeyboard(): Boolean {
    try {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    } catch (ignored: RuntimeException) {
    }
    return false
}

fun Int.toDp(): Int = (this / Resources.getSystem().displayMetrics.density).toInt()

fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

fun Float.toDp(): Float = (this / Resources.getSystem().displayMetrics.density)

fun Float.toPx(): Float = (this * Resources.getSystem().displayMetrics.density)

fun View.setAndPropagateUpFitsSystemWindows(enabled: Boolean = false) {
    fitsSystemWindows = enabled
    var view = this
    while (view.parent != null) {
        val parent = view.parent as View
        parent.fitsSystemWindows = enabled
        view = parent
    }
}

fun View.setAllParentsClip(enabled: Boolean = false) {
    var view = this
    while (view.parent != null && view.parent is ViewGroup) {
        val viewGroup = view.parent as ViewGroup
        viewGroup.clipChildren = enabled
        viewGroup.clipToPadding = enabled
        view = viewGroup
    }
}

fun CompoundButton.quietlySetIsChecked(
    newCheckedState: Boolean,
    changeListener: CompoundButton.OnCheckedChangeListener?,
) {
    setOnCheckedChangeListener(null)
    isChecked = newCheckedState
    setOnCheckedChangeListener(changeListener)
}

fun View.makeSnackbarWithNoBottomInset(
    text: CharSequence,
    @Duration duration: Int,
): Snackbar {
    val snackbar = Snackbar.make(this, text, duration)
    snackbar.isGestureInsetBottomIgnored = true
    return snackbar
}

fun View.makeSnackbarWithNoBottomInset(
    @StringRes resId: Int,
    @Duration duration: Int,
): Snackbar {
    val snackbar = Snackbar.make(this, resId, duration)
    snackbar.isGestureInsetBottomIgnored = true
    return snackbar
}

fun Slider.quietlySetValue(
    newValue: Float,
    listener: Slider.OnChangeListener,
) {
    removeOnChangeListener(listener)
    value = newValue
    addOnChangeListener(listener)
}

fun View.recursiveEnable(enabled: Boolean) {
    (this as? ViewGroup)?.children?.forEach {
        it.isEnabled = enabled
        it.recursiveEnable(enabled)
    }
}

fun View.setEnabledOpacity(enabled: Boolean) {
    alpha = if (enabled) {
        1f
    } else {
        0.4f
    }
}

/** dp size to px size. */
internal fun View.dp2Px(dp: Int): Float {
    val scale = resources.displayMetrics.density
    return dp * scale
}

/** dp size to px size. */
internal fun View.dp2Px(dp: Float): Float {
    val scale = resources.displayMetrics.density
    return dp * scale
}

/**
 * Executes [block] with the View's layoutParams and reassigns the layoutParams with the
 * updated version.
 *
 * @see View.getLayoutParams
 * @see View.setLayoutParams
 **/
internal inline fun View.updateLayoutParams(block: ViewGroup.LayoutParams.() -> Unit) {
    updateLayoutParam(this, block)
}

fun View.visibilityChanged(action: (View) -> Unit) {
    this.viewTreeObserver.addOnGlobalLayoutListener {
        val newVis: Int = this.visibility
        if (this.tag as Int? != newVis) {
            this.tag = this.visibility

            // visibility has changed
            action(this)
        }
    }
}

/**
 * Checks if the view is completely inside the screen.
 *
 * This function calculates the view's position on the screen and compares it with the screen's dimensions
 * to determine if the view is fully contained within the screen boundaries.
 *
 * @return `true` if the view is completely inside the screen, `false` otherwise.
 */
fun View.isInsideScreen(): Boolean {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    val screenRect = Rect(
        0,
        0,
        this.context.resources.displayMetrics.widthPixels,
        this.context.resources.displayMetrics.heightPixels,
    )
    val viewRect = Rect(
        location[0],
        location[1],
        location[0] + this.width,
        location[1] + this.height,
    )
    return screenRect.contains(viewRect)
}

/**
 * Checks if the view is partially inside the screen.
 *
 * This function calculates the view's position on the screen and compares it with the screen's dimensions
 * to determine if the view is partially contained within the screen boundaries.
 *
 * @return `true` if the view is partially inside the screen, `false` otherwise.
 */
fun View.isPartiallyOnScreen(): Boolean {
    val location = IntArray(2)
    this.getLocationOnScreen(location)
    val screenRect = Rect(
        0,
        0,
        this.context.resources.displayMetrics.widthPixels,
        this.context.resources.displayMetrics.heightPixels,
    )
    val viewRect = Rect(
        location[0],
        location[1],
        location[0] + this.width,
        location[1] + this.height,
    )
    return screenRect.intersect(viewRect)
}

/**
 * Executes [block] with a typed version of the View's layoutParams and reassigns the
 * layoutParams with the updated version.
 *
 * @see View.getLayoutParams
 * @see View.setLayoutParams
 **/
private inline fun <reified T : ViewGroup.LayoutParams> updateLayoutParam(
    view: View,
    block: T.() -> Unit,
) {
    val params = view.layoutParams as T
    block(params)
    view.layoutParams = params
}
