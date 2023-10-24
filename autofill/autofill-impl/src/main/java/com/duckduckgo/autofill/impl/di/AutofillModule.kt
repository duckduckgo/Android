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

package com.duckduckgo.autofill.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.AutofillFragmentResultsPlugin
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.impl.encoding.UrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.store.ALL_MIGRATIONS as AutofillMigrations
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.CredentialsSyncMetadataDao
import com.duckduckgo.autofill.store.InternalTestUserStore
import com.duckduckgo.autofill.store.LastUpdatedTimeProvider
import com.duckduckgo.autofill.store.RealAutofillPrefsStore
import com.duckduckgo.autofill.store.RealInternalTestUserStore
import com.duckduckgo.autofill.store.RealLastUpdatedTimeProvider
import com.duckduckgo.autofill.store.feature.AutofillDefaultStateDecider
import com.duckduckgo.autofill.store.feature.AutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.RealAutofillDefaultStateDecider
import com.duckduckgo.autofill.store.feature.RealAutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.email.incontext.ALL_MIGRATIONS as EmailInContextMigrations
import com.duckduckgo.autofill.store.feature.email.incontext.EmailProtectionInContextDatabase
import com.duckduckgo.autofill.store.feature.email.incontext.EmailProtectionInContextFeatureRepository
import com.duckduckgo.autofill.store.feature.email.incontext.RealEmailProtectionInContextFeatureRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class AutofillModule {

    @Provides
    fun provideInternalTestUserStore(applicationContext: Context): InternalTestUserStore = RealInternalTestUserStore(applicationContext)

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provide(): LastUpdatedTimeProvider = RealLastUpdatedTimeProvider()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAutofillPrefsStore(
        context: Context,
        autofillDefaultStateDecider: AutofillDefaultStateDecider,
    ): AutofillPrefsStore {
        return RealAutofillPrefsStore(context, autofillDefaultStateDecider)
    }

    @Provides
    fun providerAutofillDefaultStateProvider(
        userBrowserProperties: UserBrowserProperties,
        autofillFeature: AutofillFeature,
        internalTestUserChecker: InternalTestUserChecker,
    ): AutofillDefaultStateDecider {
        return RealAutofillDefaultStateDecider(
            userBrowserProperties = userBrowserProperties,
            autofillFeature = autofillFeature,
            internalTestUserChecker = internalTestUserChecker,
        )
    }

    @Provides
    fun provideAutofillUrlMatcher(unicodeNormalizer: UrlUnicodeNormalizer): AutofillUrlMatcher = AutofillDomainNameUrlMatcher(unicodeNormalizer)

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAutofillDatabase(context: Context): AutofillDatabase {
        return Room.databaseBuilder(context, AutofillDatabase::class.java, "autofill.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*AutofillMigrations)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEmailInContextDatabase(context: Context): EmailProtectionInContextDatabase {
        return Room.databaseBuilder(context, EmailProtectionInContextDatabase::class.java, "emailInContext.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*EmailInContextMigrations)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAutofillRepository(
        database: AutofillDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): AutofillFeatureRepository {
        return RealAutofillFeatureRepository(database, coroutineScope, dispatcherProvider)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideEmailInContextRepository(
        database: EmailProtectionInContextDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): EmailProtectionInContextFeatureRepository {
        return RealEmailProtectionInContextFeatureRepository(database, coroutineScope, dispatcherProvider)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesCredentialsSyncDao(
        database: AutofillDatabase,
    ): CredentialsSyncMetadataDao {
        return database.credentialsSyncDao()
    }
}

/**
 * Used to generate a plugin point for autofill result handlers
 */
@ContributesPluginPoint(scope = AppScope::class, boundType = AutofillFragmentResultsPlugin::class)
interface UnusedAutofillResultPlugin
