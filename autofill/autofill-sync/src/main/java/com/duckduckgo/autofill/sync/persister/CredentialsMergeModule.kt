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

package com.duckduckgo.autofill.sync.persister

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.sync.SyncCredentialsMapper
import com.duckduckgo.autofill.sync.SyncLoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.squareup.anvil.annotations.ContributesTo
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap

@MapKey
@Retention(AnnotationRetention.RUNTIME)
annotation class CredentialsMergeStrategyKey(val value: SyncConflictResolution)

@Module
@ContributesTo(AppScope::class)
object CredentialsMergeModule {

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.DEDUPLICATION)
    fun provideDeduplicationStrategy(
        autofillStore: AutofillStore,
        syncLoginCredentials: SyncLoginCredentials,
        syncCredentialsMapper: SyncCredentialsMapper,
        secureStorage: SecureStorage,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CrendentialsDedupStrategy(
        autofillStore,
        secureStorage,
        syncLoginCredentials,
        syncCredentialsMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.LOCAL_WINS)
    fun provideLocalWinsStrategy(
        autofillStore: AutofillStore,
        syncLoginCredentials: SyncLoginCredentials,
        syncCredentialsMapper: SyncCredentialsMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsLocalWinsStrategy(
        autofillStore,
        syncLoginCredentials,
        syncCredentialsMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.REMOTE_WINS)
    fun provideRemoteWinsStrategy(
        autofillStore: AutofillStore,
        syncLoginCredentials: SyncLoginCredentials,
        syncCredentialsMapper: SyncCredentialsMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsRemoteWinsStrategy(
        autofillStore,
        syncLoginCredentials,
        syncCredentialsMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.TIMESTAMP)
    fun provideLastModifiedWinsStrategy(
        autofillStore: AutofillStore,
        syncLoginCredentials: SyncLoginCredentials,
        syncCredentialsMapper: SyncCredentialsMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsLastModifiedWinsStrategy(
        autofillStore,
        syncLoginCredentials,
        syncCredentialsMapper,
        dispatchers,
    )
}
