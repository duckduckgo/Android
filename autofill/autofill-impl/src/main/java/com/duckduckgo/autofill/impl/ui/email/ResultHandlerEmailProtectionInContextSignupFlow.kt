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

package com.duckduckgo.autofill.impl.ui.email

import android.content.Intent
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.emailprotection.ResultHandlerEmailProtectionInContextSignupFlow
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupActivity
import com.duckduckgo.autofill.impl.jsbridge.AutofillMessagePoster
import com.duckduckgo.autofill.impl.jsbridge.response.AutofillResponseWriter
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

@ContributesBinding(FragmentScope::class)
class ResultHandlerEmailProtectionInContextSignupFlowImpl @Inject constructor(
    private val emailManager: EmailManager,
    private val responseWriter: AutofillResponseWriter,
    private val autofillMessagePoster: AutofillMessagePoster,
    private val dispatchers: DispatcherProvider,
) : ResultHandlerEmailProtectionInContextSignupFlow {

    override suspend fun onFlowEnded(data: Intent?) {
        withContext(dispatchers.io()) {
            val requestId = data?.getStringExtra(EmailProtectionInContextSignupActivity.RESULT_KEY_REQUEST_ID)
            if (requestId.isNullOrEmpty()) {
                Timber.w("Request ID is null or empty")
                return@withContext
            }
            val isSignedIn = emailManager.isSignedIn()
            val message = responseWriter.generateResponseForEmailProtectionEndOfFlow(isSignedIn)
            Timber.i("end of in-context signup flow, sending response: $message to requestId: $requestId")
            autofillMessagePoster.postMessage(message, requestId)
        }
    }
}
