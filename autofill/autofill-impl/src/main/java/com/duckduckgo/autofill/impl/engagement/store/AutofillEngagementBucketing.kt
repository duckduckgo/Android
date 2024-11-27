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

package com.duckduckgo.autofill.impl.engagement.store

import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.FEW
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.LOTS
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.MANY
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.NONE
import com.duckduckgo.autofill.impl.engagement.store.AutofillEngagementBucketing.Companion.SOME
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillEngagementBucketing {
    fun bucketNumberOfCredentials(numberOfCredentials: Int): String

    companion object {
        const val NONE = "none"
        const val FEW = "few"
        const val SOME = "some"
        const val MANY = "many"
        const val LOTS = "lots"
    }
}

@ContributesBinding(AppScope::class)
class DefaultAutofillEngagementBucketing @Inject constructor() : AutofillEngagementBucketing {

    override fun bucketNumberOfCredentials(numberOfCredentials: Int): String {
        return when {
            numberOfCredentials == 0 -> NONE
            numberOfCredentials < 4 -> FEW
            numberOfCredentials < 11 -> SOME
            numberOfCredentials < 50 -> MANY
            else -> LOTS
        }
    }
}
