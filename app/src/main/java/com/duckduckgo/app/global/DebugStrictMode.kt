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

package com.duckduckgo.app.global

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.os.strictmode.Violation
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.Executors

class DebugStrictMode {

    private val excludedVmViolations = listOf(
        { it: DdgStrictModeViolation -> "onUntaggedSocket" == it.violation.stackTrace[0]?.methodName },
        { it: DdgStrictModeViolation -> "onIncorrectContextUsed" == it.violation.stackTrace[0]?.methodName },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("android.os.strictmode.LeakedClosableViolation") },
    )

    private val excludedThreadViolations = listOf(
        { it: DdgStrictModeViolation -> it.stackTrace.contains("DuckDuckGoApplication.getCacheDir") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("DevSettingsDataStore") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("VpnSharedPreferencesProviderImpl.getSharedPreferences") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("AccessibilitySettingsSharedPreferences.getOverrideSystemFontSize") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("SecureStoreBackedAutofillStore.getAutofillAvailable") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("BrowserTabViewModel.onCtaShown") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("appReferrerDataStore.installedFromEuAuction") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("AppReferenceSharePreferences.getInstalledFromEuAuction") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("RealGpcRepository.isGpcEnabled") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("TabSwitcherAdapter.loadTabPreviewImage") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("UnsentForgetAllPixelStoreSharedPreferences.incrementCount") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("SecureStoreBackedAutofillStore.getAutofillEnabled") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("UserAgentProvider.userAgent") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("RealWebViewHttpAuthStore.clearHttpAuthUsernamePassword") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("WebViewDataManager.clearAuthentication") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("AppWidgetThemePreferences.storeWidgetSize") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("EnqueuedPixelWorker.onStateChanged") },
        // { it: DdgStrictModeViolation -> it.stackTrace.contains("SettingsSharedPreferences.setAppUsedSinceLastClear") },
        // { it: DdgStrictModeViolation -> it.stackTrace.contains("SettingsSharedPreferences.setAppBackgroundedTimestamp") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("SettingsSharedPreferences.setAppIconChanged") },
        { it: DdgStrictModeViolation -> it.stackTrace.contains("DuckDuckGoFaviconManager.loadFromDisk") },
    )

    fun enable() {

        if (!BuildConfig.DEBUG) {
            return
        }

        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectAll()
                .addCustomPenaltyListener()
                .build()
        )

        StrictMode.setVmPolicy(
            VmPolicy.Builder()
                .detectAll()
                .addCustomPenaltyListener()
                .build()
        )

        Timber.d("Enabled strict mode logger")
    }

    private fun VmPolicy.Builder.addCustomPenaltyListener(): VmPolicy.Builder {
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            this.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                GlobalScope.launch {
                    val ddgViolation = violation.toDdgViolation()
                    var shouldLog = true

                    if (excludedVmViolations.any { it(ddgViolation) }) {
                        shouldLog = false
                    }

                    if (shouldLog) {
                        log(violation, ddgViolation.stackTrace, "vm [${violation.stackTrace[0].methodName}]")
                    }
                }

            }
        } else {
            this.penaltyLog()
        }
        return this
    }

    private fun ThreadPolicy.Builder.addCustomPenaltyListener(): ThreadPolicy.Builder {
        if (VERSION.SDK_INT >= VERSION_CODES.P) {
            this.penaltyListener(Executors.newSingleThreadExecutor()) { violation ->
                GlobalScope.launch {
                    val ddgViolation = violation.toDdgViolation()
                    var shouldLog = true

                    if (excludedThreadViolations.any { it(ddgViolation) }) {
                        shouldLog = false
                    }

                    if (shouldLog) {
                        log(violation, ddgViolation.stackTrace, "thread")
                    }
                }
            }
        } else {
            this.penaltyLog()
        }
        return this
    }

    private fun log(
        violation: Violation,
        stackTrace: String,
        type: String
    ) {
        Timber.w(violation.cause, "StrictMode violation, $type: $stackTrace")
    }

    data class DdgStrictModeViolation(
        val violation: Violation,
        val stackTrace: String
    )

    private fun Violation.toDdgViolation(): DdgStrictModeViolation {
        return DdgStrictModeViolation(this, stackTraceToString())
    }
}
