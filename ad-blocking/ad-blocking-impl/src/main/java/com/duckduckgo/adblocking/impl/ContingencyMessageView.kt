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

package com.duckduckgo.adblocking.impl

import android.webkit.WebView
import androidx.annotation.UiThread
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Shows and tracks the ad-blocking contingency bottom sheet, keeping the dialog/UI concern out of
 * [ContingencyMessageHandler].
 */
interface ContingencyMessageView {
    /** Shows the contingency bottom sheet using the given [webView]'s context. */
    @UiThread
    fun show(webView: WebView)
}

@ContributesBinding(AppScope::class)
class RealContingencyMessageView @Inject constructor() : ContingencyMessageView {

    override fun show(webView: WebView) {
        ContingencyMessageBottomSheet(webView.context).show()
    }
}
