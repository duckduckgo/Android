/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.reporting

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.AutofillGlobalCapabilityChecker
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SITE_BREAKAGE_REPORT
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

interface AutofillBreakageReportSender {
    fun sendBreakageReport(
        url: String,
        privacyProtectionEnabled: Boolean?,
    )
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AutofillBreakageReportSenderImpl @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val autofillCapabilityChecker: AutofillGlobalCapabilityChecker,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val emailManager: EmailManager,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
) : AutofillBreakageReportSender {

    /**
     * website: the URL + path of the website with all query parameters removed (to identify the page with an issue e.g. checkout form vs login page)
     * language: the app’s language setting (e.g. `en`, `fr`) as autofill issues are frequently language specific
     * autofill_enabled: whether the user has the autofill feature enabled  (true / false)
     * privacy_protection : whether privacy protection was enabled for the site (true / false)
     * email_protection: whether a user has enabled email protection (true / false)
     * never_prompt: whether a user has decided if they want to be prompted to save logins for this site (true / false)
     */

    override fun sendBreakageReport(
        url: String,
        privacyProtectionEnabled: Boolean?,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            val params = mapOf(
                "website" to formatUrl(url),
                "language" to formatLanguage(),
                "autofill_enabled" to formatAutofillEnabledStatus(),
                "privacy_protection" to formatPrivacyProtectionStatus(privacyProtectionEnabled),
                "email_protection" to formatEmailProtectionStatus(),
                "never_prompt" to formatNeverProtectThisSiteStatus(url),
            )
            logcat { "Sending autofill breakage report $params" }

            pixel.fire(AUTOFILL_SITE_BREAKAGE_REPORT, parameters = params)
        }
    }

    private suspend fun formatNeverProtectThisSiteStatus(url: String): String {
        return neverSavedSiteRepository.isInNeverSaveList(url).toString()
    }

    private fun formatEmailProtectionStatus(): String {
        return emailManager.isSignedIn().toString()
    }

    private fun formatPrivacyProtectionStatus(privacyProtectionEnabled: Boolean?): String {
        return when (privacyProtectionEnabled) {
            null -> "unknown"
            else -> privacyProtectionEnabled.toString()
        }
    }

    private suspend fun formatAutofillEnabledStatus(): String {
        return autofillCapabilityChecker.isAutofillEnabledByUser().toString()
    }

    private fun formatUrl(url: String): String {
        val uri = url.toUri()
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(uri.path)
            .fragment(uri.fragment)
            .build()
            .toString()
    }

    private fun formatLanguage(): String {
        return appBuildConfig.deviceLocale.language
    }
}
