package com.duckduckgo.app.browser.longpress

import android.webkit.WebView.HitTestResult.IMAGE_TYPE
import android.webkit.WebView.HitTestResult.SRC_ANCHOR_TYPE
import android.webkit.WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE

enum class LongPressMenuShape { LINK, IMAGE, IMAGE_LINK }

data class LongPressMenuConfig(
    val shape: LongPressMenuShape,
    val primaryRowIsFireTab: Boolean,
    val showDedicatedFireTabRow: Boolean,
)

/**
 * Decides which branded long-press popup to show for a given hit-test type and browser mode.
 * Returns null for unsupported hit-test types (caller falls back to the native context menu).
 */
fun longPressMenuConfigFor(
    hitTestType: Int,
    isFireMode: Boolean,
): LongPressMenuConfig? {
    val shape = when (hitTestType) {
        SRC_ANCHOR_TYPE -> LongPressMenuShape.LINK
        IMAGE_TYPE -> LongPressMenuShape.IMAGE
        SRC_IMAGE_ANCHOR_TYPE -> LongPressMenuShape.IMAGE_LINK
        else -> return null
    }
    val hasLinkActions = shape != LongPressMenuShape.IMAGE
    return LongPressMenuConfig(
        shape = shape,
        primaryRowIsFireTab = hasLinkActions && isFireMode,
        showDedicatedFireTabRow = hasLinkActions && !isFireMode,
    )
}
