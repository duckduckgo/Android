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

package com.duckduckgo.autofill

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.FakePasswordStoreEventPlugin
import com.duckduckgo.autofill.impl.SecureStoreBackedAutofillStore
import com.duckduckgo.autofill.impl.deduper.AutofillLoginDeduplicator
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.LastUpdatedTimeProvider
import com.duckduckgo.autofill.sync.CredentialsSyncMetadata
import com.duckduckgo.autofill.sync.SyncCredentialsListener
import com.duckduckgo.autofill.sync.inMemoryAutofillDatabase
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.mock

fun fakeAutofillStore(
    secureStorage: SecureStorage = fakeStorage(),
    lastUpdatedTimeProvider: LastUpdatedTimeProvider = lastUpdatedTimeProvider(),
    autofillPrefsStore: AutofillPrefsStore,
    autofillUrlMatcher: AutofillUrlMatcher = autofillDomainNameUrlMatcher(),
    autofillFeature: AutofillFeature,
    dispatcherProvider: DispatcherProvider,
    coroutineScope: CoroutineScope,
) = SecureStoreBackedAutofillStore(
    secureStorage = secureStorage,
    lastUpdatedTimeProvider = lastUpdatedTimeProvider,
    autofillPrefsStore = autofillPrefsStore,
    dispatcherProvider = dispatcherProvider,
    autofillUrlMatcher = autofillUrlMatcher,
    passwordStoreEventListenersPlugins = FakePasswordStoreEventPlugin(),
    syncCredentialsListener = SyncCredentialsListener(
        CredentialsSyncMetadata(inMemoryAutofillDatabase().credentialsSyncDao()),
        dispatcherProvider,
        coroutineScope,
    ),
    autofillFeature = autofillFeature,
    usernameComparer = mock(),
)

fun fakeStorage(
    canAccessSecureStorage: Boolean = true,
    urlMatcher: AutofillUrlMatcher = autofillDomainNameUrlMatcher(),
) = FakeSecureStore(
    canAccessSecureStorage = canAccessSecureStorage,
    urlMatcher = urlMatcher,
)

fun lastUpdatedTimeProvider() = object : LastUpdatedTimeProvider {
    override fun getInMillis(): Long = 10000L
}

fun autofillDomainNameUrlMatcher() = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())

fun noopDeduplicator() = object : AutofillLoginDeduplicator {
    override suspend fun deduplicate(
        originalUrl: String,
        logins: List<LoginCredentials>,
    ): List<LoginCredentials> = logins
}

fun noopGroupBuilder() = object : AutofillSelectCredentialsGrouper {
    override fun group(
        originalUrl: String,
        unsortedCredentials: List<LoginCredentials>,
    ): Groups {
        return Groups(unsortedCredentials, emptyMap(), emptyMap())
    }
}
