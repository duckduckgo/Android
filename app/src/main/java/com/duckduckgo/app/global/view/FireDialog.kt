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

package com.duckduckgo.app.global.view

import android.content.Context
import android.support.design.widget.BottomSheetDialog
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.WebDataManager
import kotlinx.android.synthetic.main.sheet_fire_clear_data.*

class FireDialog(
    context: Context,
    webDataManager: WebDataManager,
    clearStarted: (() -> Unit),
    clearComplete: (() -> Unit)
) :
    BottomSheetDialog(context) {
    init {
        val contentView = View.inflate(getContext(), R.layout.sheet_fire_clear_data, null)
        setContentView(contentView)
        clearAllOption.setOnClickListener {
            clearStarted()
            webDataManager.clearData(WebView(context), WebStorage.getInstance(), context)
            webDataManager.clearWebViewSessions()
            webDataManager.clearExternalCookies(CookieManager.getInstance(), clearComplete)
            dismiss()
        }
        cancelOption.setOnClickListener {
            dismiss()
        }
    }

}