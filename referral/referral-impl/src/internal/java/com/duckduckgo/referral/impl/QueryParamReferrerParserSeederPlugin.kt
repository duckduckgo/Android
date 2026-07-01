/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.referral.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class QueryParamReferrerParserSeederPlugin @Inject constructor(
    private val appInstallationReferrerParser: AppInstallationReferrerParser,
) : TestSeederPlugin {
    override val handledKeys: Set<String>
        get() = setOf(TestSeederKey.REFERRER.key)

    override suspend fun apply(
        key: String,
        value: String,
    ) {
        if (key == TestSeederKey.REFERRER.key) {
            appInstallationReferrerParser.parse(referrer = value)
        }
    }
}
