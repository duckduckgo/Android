/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.systemautofill

import android.annotation.SuppressLint
import android.os.Build
import android.view.autofill.AutofillManager
import android.webkit.WebView
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SystemAutofillServiceSuppressor {
    fun suppressAutofill(webView: WebView?)
}

@ContributesBinding(AppScope::class)
class RealSystemAutofillServiceSuppressor @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) : SystemAutofillServiceSuppressor {

    @SuppressLint("NewApi")
    override fun suppressAutofill(webView: WebView?) {
        if (appBuildConfig.versionCode >= Build.VERSION_CODES.O) {
            webView?.context?.getSystemService(AutofillManager::class.java)?.cancel()
        }
    }
}
