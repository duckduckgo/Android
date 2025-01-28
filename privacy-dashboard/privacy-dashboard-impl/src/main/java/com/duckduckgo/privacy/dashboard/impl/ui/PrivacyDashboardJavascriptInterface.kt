/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.webkit.JavascriptInterface

class PrivacyDashboardJavascriptInterface constructor(
    val onPrivacyProtectionsClicked: (String) -> Unit,
    val onUrlClicked: (String) -> Unit,
    val onOpenSettings: (String) -> Unit,
    val onClose: () -> Unit,
    val onSubmitBrokenSiteReport: (String) -> Unit,
    val onGetToggleReportOptions: () -> Unit,
    val onSendToggleReport: () -> Unit,
    val onRejectToggleReport: () -> Unit,
    val onSeeWhatIsSent: () -> Unit,
    val onShowNativeFeedback: () -> Unit,
    val onReportBrokenSiteShown: () -> Unit,
) {
    @JavascriptInterface
    fun toggleAllowlist(payload: String) {
        onPrivacyProtectionsClicked(payload)
    }

    @JavascriptInterface
    fun close() {
        onClose()
    }

    @JavascriptInterface
    fun showBreakageForm() {
        // FE handles navigation internally, but we must keep this callback for it to work.
    }

    @JavascriptInterface
    fun openInNewTab(payload: String) {
        onUrlClicked(payload)
    }

    @JavascriptInterface
    fun openSettings(payload: String) {
        onOpenSettings(payload)
    }

    @JavascriptInterface
    fun submitBrokenSiteReport(payload: String) {
        onSubmitBrokenSiteReport(payload)
    }

    @JavascriptInterface
    fun getToggleReportOptions() {
        onGetToggleReportOptions()
    }

    @JavascriptInterface
    fun sendToggleReport() {
        onSendToggleReport()
    }

    @JavascriptInterface
    fun rejectToggleReport() {
        onRejectToggleReport()
    }

    @JavascriptInterface
    fun seeWhatIsSent() {
        onSeeWhatIsSent()
    }

    @JavascriptInterface
    fun showNativeFeedback() {
        onShowNativeFeedback()
    }

    @JavascriptInterface
    fun reportBrokenSiteShown() {
        onReportBrokenSiteShown()
    }

    companion object {
        // Interface name used inside js layer
        const val JAVASCRIPT_INTERFACE_NAME = "PrivacyDashboard"
    }
}
