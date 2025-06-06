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

package com.duckduckgo.autofill.impl.securestorage.di

import android.content.Context
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.securestorage.DerivedKeySecretFactory
import com.duckduckgo.autofill.impl.securestorage.RealDerivedKeySecretFactory
import com.duckduckgo.autofill.store.RealSecureStorageKeyRepository
import com.duckduckgo.autofill.store.SecureStorageKeyRepository
import com.duckduckgo.autofill.store.keys.RealSecureStorageKeyStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
object SecureStorageModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSecureStorageKeyStore(
        context: Context,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        autofillFeature: AutofillFeature,
    ): SecureStorageKeyRepository =
        RealSecureStorageKeyRepository(
            RealSecureStorageKeyStore(context, coroutineScope, dispatcherProvider, autofillFeature),
        )
}

@Module
@ContributesTo(AppScope::class)
object SecureStorageKeyModule {
    @Provides
    @Named("DerivedKeySecretFactoryFor26Up")
    fun provideDerivedKeySecretFactoryFor26Up(): DerivedKeySecretFactory = RealDerivedKeySecretFactory()
}
