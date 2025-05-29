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

package com.duckduckgo.autofill.store.feature

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.UserBrowserProperties
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat

interface AutofillDefaultStateDecider {
    fun defaultState(): Boolean
}

class RealAutofillDefaultStateDecider(
    private val userBrowserProperties: UserBrowserProperties,
    private val autofillFeature: AutofillFeature,
    private val internalTestUserChecker: InternalTestUserChecker,
) : AutofillDefaultStateDecider {

    override fun defaultState(): Boolean {
        if (internalTestUserChecker.isInternalTestUser) {
            logcat(VERBOSE) { "Internal testing user, enabling autofill by default" }
            return true
        }

        if (!autofillFeature.onByDefault().isEnabled()) {
            return false
        }

        if (userBrowserProperties.daysSinceInstalled() > 0L && !autofillFeature.onForExistingUsers().isEnabled()) {
            return false
        }

        logcat(INFO) { "Determined Autofill should be enabled by default" }
        return true
    }
}
