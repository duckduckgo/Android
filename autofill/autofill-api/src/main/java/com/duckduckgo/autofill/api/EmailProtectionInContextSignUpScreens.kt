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

package com.duckduckgo.autofill.api

import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Launch params for starting In-Context Email Protection flow
 * @param messageRequestId The ID of the original web message that triggered the flow, used to send a reply back to the web page
 */
data class EmailProtectionInContextSignUpStartScreen(val messageRequestId: String) : GlobalActivityStarter.ActivityParams

/**
 * Launch params for resuming In-Context Email Protection flow from an email verification link
 * @param url The URL of the email verification link
 * @param messageRequestId The ID of the original web message that triggered the flow, used to send a reply back to the web page
 */
data class EmailProtectionInContextSignUpHandleVerificationLink(val url: String, val messageRequestId: String) : GlobalActivityStarter.ActivityParams

/**
 * Activity result codes
 */
object EmailProtectionInContextSignUpScreenResult {
    const val SUCCESS = 1
    const val CANCELLED = 2

    const val RESULT_KEY_MESSAGE = "message"
    const val RESULT_KEY_REQUEST_ID = "requestId"
}
