/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.di

import android.content.Context
import com.duckduckgo.autofill.InternalTestUserChecker
import com.duckduckgo.autofill.store.AutofillStore
import com.duckduckgo.autofill.store.InternalTestUserStore
import com.duckduckgo.autofill.store.RealAutofillPrefsStore
import com.duckduckgo.autofill.store.RealInternalTestUserStore
import com.duckduckgo.autofill.store.RealLastUpdatedTimeProvider
import com.duckduckgo.autofill.store.SecureStoreBackedAutofillStore
import com.duckduckgo.autofill.store.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.ui.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class AutofillModule {

    @Provides
    fun provideInternalTestUserStore(applicationContext: Context): InternalTestUserStore = RealInternalTestUserStore(applicationContext)

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun autofillStore(
        secureStorage: SecureStorage,
        context: Context,
        internalTestUserChecker: InternalTestUserChecker,
        autofillUrlMatcher: AutofillUrlMatcher,
    ): AutofillStore {
        return SecureStoreBackedAutofillStore(
            secureStorage = secureStorage,
            internalTestUserChecker = internalTestUserChecker,
            lastUpdatedTimeProvider = RealLastUpdatedTimeProvider(),
            autofillPrefsStore = RealAutofillPrefsStore(context, internalTestUserChecker),
            autofillUrlMatcher = autofillUrlMatcher,
        )
    }

    @Provides
    fun provideAutofillUrlMatcher(): AutofillUrlMatcher = AutofillDomainNameUrlMatcher()
}
