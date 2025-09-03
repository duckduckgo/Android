/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service.store

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.di.ProcessName
import com.duckduckgo.autofill.impl.service.AutofillServiceFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

interface AutofillServiceFeatureRepository {
    val exceptions: CopyOnWriteArrayList<String>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AutofillServiceFeatureRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AutofillServiceFeatureRepository::class,
)
class RealAutofillServiceFeatureRepository @Inject constructor(
    @IsMainProcess private val isMainProcess: Boolean,
    @ProcessName private val processName: String,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillServiceFeature: AutofillServiceFeature,
) : AutofillServiceFeatureRepository, PrivacyConfigCallbackPlugin {

    override val exceptions = CopyOnWriteArrayList<String>()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat(INFO) { "DDGAutofillService: Init AutofillFeatureRepository from $processName" }
            loadToMemory()
        }
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    private fun loadToMemory() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess || processName == ":autofill") {
                exceptions.clear()
                exceptions.addAll(autofillServiceFeature.self().getExceptions().map { it.domain })
            }
        }
    }
}
