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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration.AutofillJsConfigType
import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration.AutofillJsConfigType.Debug
import com.duckduckgo.autofill.impl.configuration.AutofillJavascriptEnvironmentConfiguration.AutofillJsConfigType.Production
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutofillJavascriptEnvironmentConfiguration {

    fun useProductionConfig()
    fun useDebugConfig()
    fun getConfigType(): AutofillJsConfigType

    sealed interface AutofillJsConfigType {
        data object Production : AutofillJsConfigType
        data object Debug : AutofillJsConfigType
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultAutofillJavascriptEnvironmentConfiguration @Inject constructor() : AutofillJavascriptEnvironmentConfiguration {

    private var configType: AutofillJsConfigType = Production

    override fun useProductionConfig() {
        configType = Production
    }

    override fun useDebugConfig() {
        configType = Debug
    }

    override fun getConfigType(): AutofillJsConfigType = configType
}
