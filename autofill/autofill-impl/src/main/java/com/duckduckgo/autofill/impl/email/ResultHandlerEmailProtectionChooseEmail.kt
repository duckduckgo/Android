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

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.LAST_USED_DAY
import com.duckduckgo.autofill.api.AutofillEventListener
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.Companion.KEY_RESULT
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.Companion.KEY_URL
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.DoNotUseEmailProtection
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.UsePersonalEmailAddress
import com.duckduckgo.autofill.api.EmailProtectionChooseEmailDialog.UseEmailResultType.UsePrivateAliasAddress
import com.duckduckgo.autofill.api.credential.saving.DuckAddressLoginCreator
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_TOOLTIP_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ADDRESS
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.EMAIL_USE_ALIAS
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesMultibinding(FragmentScope::class)
class ResultHandlerEmailProtectionChooseEmail @Inject constructor(
    private val emailManager: EmailManager,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
    private val messagePoster: AutofillMessagePoster,
    private val loginCreator: DuckAddressLoginCreator,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AutofillFragmentResultsPlugin {

    override fun processResult(
        result: Bundle,
        context: Context,
        tabId: String,
        fragment: Fragment,
        autofillCallback: AutofillEventListener,
    ) {
        Timber.d("${this::class.java.simpleName}: processing result")

        val userSelection = BundleCompat.getParcelable(result, KEY_RESULT, UseEmailResultType::class.java) ?: return
        val autofillWebMessageRequest = BundleCompat.getParcelable(result, KEY_URL, AutofillWebMessageRequest::class.java) ?: return

        when (userSelection) {
            UsePersonalEmailAddress -> onSelectedToUsePersonalAddress(autofillWebMessageRequest)
            UsePrivateAliasAddress -> onSelectedToUsePrivateAlias(autofillWebMessageRequest, tabId)
            DoNotUseEmailProtection -> onSelectedNotToUseEmailProtection(autofillWebMessageRequest)
        }
    }

    private fun onSelectedToUsePersonalAddress(autofillWebMessageRequest: AutofillWebMessageRequest) {
        appCoroutineScope.launch(dispatchers.io()) {
            val duckAddress = emailManager.getEmailAddress() ?: return@launch

            enqueueEmailProtectionPixel(EMAIL_USE_ADDRESS, includeLastUsedDay = true)

            withContext(dispatchers.io()) {
                val message = buildResponseMessage(duckAddress)
                messagePoster.postMessage(message, autofillWebMessageRequest.requestId)
            }

            emailManager.setNewLastUsedDate()
        }
    }

    private fun onSelectedToUsePrivateAlias(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        tabId: String,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            val privateAlias = emailManager.getAlias() ?: return@launch

            enqueueEmailProtectionPixel(EMAIL_USE_ALIAS, includeLastUsedDay = true)

            val message = buildResponseMessage(privateAlias)
            messagePoster.postMessage(message, autofillWebMessageRequest.requestId)

            loginCreator.createLoginForPrivateDuckAddress(
                duckAddress = privateAlias,
                tabId = tabId,
                originalUrl = autofillWebMessageRequest.requestOrigin,
            )

            emailManager.setNewLastUsedDate()
        }
    }

    private fun buildResponseMessage(emailAddress: String): String {
        return """
            {
                "success": {
                    "alias": "${emailAddress.removeSuffix("@duck.com")}"
                }
            }
        """.trimIndent()
    }

    private fun onSelectedNotToUseEmailProtection(autofillWebMessageRequest: AutofillWebMessageRequest) {
        val message = buildResponseMessage("")
        messagePoster.postMessage(message, autofillWebMessageRequest.requestId)
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

    override fun resultKey(tabId: String): String {
        return EmailProtectionChooseEmailDialog.resultKey(tabId)
    }
}
