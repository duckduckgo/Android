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
import android.webkit.WebView
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.DownloadFile
import com.duckduckgo.app.browser.LongPressHandler.RequiredAction.None
import timber.log.Timber
import javax.inject.Inject


interface LongPressHandler {
    fun handleLongPress(longPressTargetType: Int, menu: ContextMenu)
    fun userSelectedMenuItem(longPressTarget: String, item: MenuItem): RequiredAction

    sealed class RequiredAction {
        object None : RequiredAction()
        class DownloadFile(val url: String) : RequiredAction()
    }
}

class WebViewLongPressHandler @Inject constructor() : LongPressHandler {

    override fun handleLongPress(longPressTargetType: Int, menu: ContextMenu) {
        when (longPressTargetType) {
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                menu.setHeaderTitle(R.string.imageOptions)
                menu.add(0, CONTEXT_MENU_ID_DOWNLOAD_IMAGE, 0, R.string.downloadImage)
            }
            else -> Timber.v("App does not yet handle target type: $longPressTargetType")
        }
    }

    override fun userSelectedMenuItem(longPressTarget: String, item: MenuItem): RequiredAction {
        return when (item.itemId) {
            CONTEXT_MENU_ID_DOWNLOAD_IMAGE -> {
                return DownloadFile(longPressTarget)
            }
            else -> None
        }
    }


    companion object {
        const val CONTEXT_MENU_ID_DOWNLOAD_IMAGE = 1
    }
}