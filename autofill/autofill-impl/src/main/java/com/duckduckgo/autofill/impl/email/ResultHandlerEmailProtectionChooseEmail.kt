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

package com.duckduckgo.autofill.impl.email

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.LAST_USED_DAY
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.*
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.engagement.DataAutofilledListener
import com.duckduckgo.autofill.impl.partialsave.PartialCredentialSaveStore
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesMultibinding(AppScope::class)
class ResultHandlerEmailProtectionChooseEmail @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val emailManager: EmailManager,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val autofilledListeners: PluginPoint<DataAutofilledListener>,
    private val partialCredentialSaveStore: PartialCredentialSaveStore,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        logcat { "${this::class.java.simpleName}: processing result" }

        val userSelection: EmailProtectionChooseEmailDialog.UseEmailResultType =
            result.safeGetParcelable(EmailProtectionChooseEmailDialog.KEY_RESULT) ?: return
        val originalUrl = result.getString(EmailProtectionChooseEmailDialog.KEY_URL) ?: return

        when (userSelection) {
            UsePersonalEmailAddress -> {
                onSelectedToUsePersonalAddress(originalUrl, autofillCallback)
                notifyAutofillListenersDuckAddressFilled()
            }
            UsePrivateAliasAddress -> {
                onSelectedToUsePrivateAlias(originalUrl, autofillCallback)
                notifyAutofillListenersDuckAddressFilled()
            }
            DoNotUseEmailProtection -> onSelectedNotToUseEmailProtection()
        }
    }

    private fun onSelectedToUsePersonalAddress(originalUrl: String, autofillCallback: AutofillEventListener) {
        appCoroutineScope.launch(dispatchers.io()) {
            val duckAddress = emailManager.getEmailAddress() ?: return@launch

            enqueueEmailProtectionPixel(EMAIL_USE_ADDRESS, includeLastUsedDay = true)

            withContext(dispatchers.main()) {
                autofillCallback.onUseEmailProtectionPersonalAddress(originalUrl, duckAddress)
            }

            emailManager.setNewLastUsedDate()
            partialCredentialSaveStore.saveUsername(originalUrl, duckAddress)
        }
    }

    private fun onSelectedToUsePrivateAlias(originalUrl: String, autofillCallback: AutofillEventListener) {
        appCoroutineScope.launch(dispatchers.io()) {
            val privateAlias = emailManager.getAlias() ?: return@launch

            enqueueEmailProtectionPixel(EMAIL_USE_ALIAS, includeLastUsedDay = true)

            withContext(dispatchers.main()) {
                autofillCallback.onUseEmailProtectionPrivateAlias(originalUrl, privateAlias)
            }

            emailManager.setNewLastUsedDate()
            partialCredentialSaveStore.saveUsername(originalUrl, privateAlias)
        }
    }

    private fun onSelectedNotToUseEmailProtection() {
        enqueueEmailProtectionPixel(EMAIL_TOOLTIP_DISMISSED, includeLastUsedDay = false)
    }

    private fun enqueueEmailProtectionPixel(
        pixelName: PixelName,
        includeLastUsedDay: Boolean,
    ) {
        val map = mutableMapOf(PixelParameter.COHORT to emailManager.getCohort())
        if (includeLastUsedDay) {
            map[LAST_USED_DAY] = emailManager.getLastUsedDate()
        }

        pixel.enqueueFire(
            pixelName,
            map,
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    private inline fun <reified T : Parcelable> Bundle.safeGetParcelable(key: String) =
        if (appBuildConfig.sdkInt >= 33) {
            getParcelable(key, T::class.java)
        } else {
            getParcelable(key)
        }

    override fun resultKey(tabId: String): String {
        return EmailProtectionChooseEmailDialog.resultKey(tabId)
    }

    private fun notifyAutofillListenersDuckAddressFilled() {
        autofilledListeners.getPlugins().forEach {
            it.onAutofilledDuckAddress()
        }
    }
}
