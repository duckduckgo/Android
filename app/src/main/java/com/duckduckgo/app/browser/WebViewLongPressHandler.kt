/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.view.ContextMenu
import android.view.MenuItem
import android.webkit.URLUtil
import android.webkit.WebView
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.*
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import timber.log.Timber
import javax.inject.Inject


interface LongPressHandler {
    fun handleLongPress(longPressTargetType: Int, longPressTargetUrl: String?, menu: ContextMenu)
    fun userSelectedMenuItem(longPressTarget: String, item: MenuItem): RequiredAction

    sealed class RequiredAction {
        object None : RequiredAction()
        class OpenInNewTab(val url: String) : RequiredAction()
        class OpenInNewBackgroundTab(val url: String) : RequiredAction()
        class DownloadFile(val url: String) : RequiredAction()
        class ShareLink(val url: String) : RequiredAction()
    }
}

class WebViewLongPressHandler @Inject constructor(private val pixel: Pixel) : LongPressHandler {

    override fun handleLongPress(longPressTargetType: Int, longPressTargetUrl: String?, menu: ContextMenu) {
        var menuShown = true
        when (longPressTargetType) {
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                if (URLUtil.isNetworkUrl(longPressTargetUrl) || URLUtil.isDataUrl(longPressTargetUrl)) {
                    menu.setHeaderTitle(R.string.imageOptions)
                    menu.add(0, CONTEXT_MENU_ID_DOWNLOAD_IMAGE, 0, R.string.downloadImage)
                }
            }
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                menu.setHeaderTitle(R.string.linkOptions)
                menu.add(0, CONTEXT_MENU_ID_OPEN_IN_NEW_TAB, 1, R.string.openInNewTab)
                menu.add(0, CONTEXT_MENU_ID_OPEN_IN_NEW_BACKGROUND_TAB, 2, R.string.openInNewBackgroundTab)
                menu.add(0, CONTEXT_MENU_ID_SHARE_LINK, 3, R.string.shareLink)
            }
            else -> {
                Timber.v("App does not yet handle target type: $longPressTargetType")
                menuShown = false
            }
        }

        if (menuShown) {
            pixel.fire(LONG_PRESS)
        }

    }

    override fun userSelectedMenuItem(longPressTarget: String, item: MenuItem): RequiredAction {
        return when (item.itemId) {
            CONTEXT_MENU_ID_OPEN_IN_NEW_TAB -> {
                pixel.fire(LONG_PRESS_NEW_TAB)
                return OpenInNewTab(longPressTarget)
            }
            CONTEXT_MENU_ID_OPEN_IN_NEW_BACKGROUND_TAB -> {
                pixel.fire(LONG_PRESS_NEW_BACKGROUND_TAB)
                return OpenInNewBackgroundTab(longPressTarget)
            }
            CONTEXT_MENU_ID_DOWNLOAD_IMAGE -> {
                pixel.fire(LONG_PRESS_DOWNLOAD_IMAGE)
                return DownloadFile(longPressTarget)
            }
            CONTEXT_MENU_ID_SHARE_LINK -> {
                pixel.fire(LONG_PRESS_SHARE)
                return ShareLink(longPressTarget)
            }
            else -> None
        }
    }


    companion object {
        const val CONTEXT_MENU_ID_OPEN_IN_NEW_TAB = 1
        const val CONTEXT_MENU_ID_DOWNLOAD_IMAGE = 2
        const val CONTEXT_MENU_ID_SHARE_LINK = 3
        const val CONTEXT_MENU_ID_OPEN_IN_NEW_BACKGROUND_TAB = 4
    }
}