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

import com.duckduckgo.autofill.sync.*
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
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
        credentialsSync: CredentialsSync,
        credentialsSyncMapper: CredentialsSyncMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsDedupStrategy(
        credentialsSync,
        credentialsSyncMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.LOCAL_WINS)
    fun provideLocalWinsStrategy(
        credentialsSync: CredentialsSync,
        credentialsSyncMapper: CredentialsSyncMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsLocalWinsStrategy(
        credentialsSync,
        credentialsSyncMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.REMOTE_WINS)
    fun provideRemoteWinsStrategy(
        credentialsSync: CredentialsSync,
        credentialsSyncMapper: CredentialsSyncMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsRemoteWinsStrategy(
        credentialsSync,
        credentialsSyncMapper,
        dispatchers,
    )

    @Provides
    @IntoMap
    @CredentialsMergeStrategyKey(SyncConflictResolution.TIMESTAMP)
    fun provideLastModifiedWinsStrategy(
        credentialsSync: CredentialsSync,
        credentialsSyncMapper: CredentialsSyncMapper,
        dispatchers: DispatcherProvider,
    ): CredentialsMergeStrategy = CredentialsLastModifiedWinsStrategy(
        credentialsSync,
        credentialsSyncMapper,
        dispatchers,
    )
}
