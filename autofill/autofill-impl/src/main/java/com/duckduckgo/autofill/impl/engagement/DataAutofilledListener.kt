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

package com.duckduckgo.autofill.impl.engagement

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesPluginPoint(AppScope::class)
interface DataAutofilledListener {
    fun onAutofilledSavedPassword()
    fun onAutofilledDuckAddress()
    fun onUsedGeneratedPassword()
}

@ContributesMultibinding(AppScope::class)
class DefaultDataAutofilledListener @Inject constructor(
    private val engagementRepository: AutofillEngagementRepository,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : DataAutofilledListener {

    override fun onAutofilledSavedPassword() {
        logcat(VERBOSE) { "onAutofilledSavedPassword, recording autofilled today" }
        recordAutofilledToday()
    }

    override fun onAutofilledDuckAddress() {
        logcat(VERBOSE) { "onAutofilledDuckAddress, recording autofilled today" }
        recordAutofilledToday()
    }

    override fun onUsedGeneratedPassword() {
        logcat(VERBOSE) { "onUsedGeneratedPassword, recording autofilled today" }
        recordAutofilledToday()
    }

    private fun recordAutofilledToday() {
        appCoroutineScope.launch(dispatchers.io()) {
            engagementRepository.recordAutofilledToday()
        }
    }
}
