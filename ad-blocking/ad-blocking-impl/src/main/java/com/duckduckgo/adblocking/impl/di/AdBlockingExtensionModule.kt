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

package com.duckduckgo.adblocking.impl.di

import com.duckduckgo.adblocking.impl.domain.PublicKeyProvider
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionSettings
import com.duckduckgo.adblocking.impl.remoteconfig.DomainJsonAdapter
import com.duckduckgo.adblocking.impl.remoteconfig.ScriptletsSettings
import com.duckduckgo.adblocking.impl.store.AD_BLOCKING_EXTENSION_MIGRATIONS
import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDao
import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDatabase
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

@Module
@ContributesTo(AppScope::class)
object AdBlockingExtensionModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdBlockingExtensionDatabase(databaseProvider: DatabaseProvider): AdBlockingExtensionDatabase {
        return databaseProvider.buildRoomDatabase(
            AdBlockingExtensionDatabase::class.java,
            "ad_blocking_extension.db",
            config = RoomDatabaseConfig(
                fallbackToDestructiveMigration = true,
                migrations = AD_BLOCKING_EXTENSION_MIGRATIONS.toList(),
            ),
        )
    }

    @Provides
    fun provideAdBlockingExtensionDao(database: AdBlockingExtensionDatabase): AdBlockingExtensionDao =
        database.adBlockingExtensionDao()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideBase64Decoder(): Base64.Decoder =
        Base64.getDecoder()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideKeyFactory(): KeyFactory =
        KeyFactory.getInstance("EC")

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdBlockingExtensionPublicKey(publicKeyProvider: PublicKeyProvider): PublicKey =
        publicKeyProvider.publicKey

    @Provides
    fun provideUTF8CharsetDecoder(): CharsetDecoder =
        StandardCharsets.UTF_8.newDecoder()

    @Provides
    fun provideSignature(): Signature =
        Signature.getInstance("SHA256withECDSA")

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAdBlockingExtensionSettingsAdapter(): JsonAdapter<AdBlockingExtensionSettings> =
        Moshi.Builder()
            .add(Domain::class.java, DomainJsonAdapter().nullSafe())
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(AdBlockingExtensionSettings::class.java)

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideScriptletsSettingsAdapter(): JsonAdapter<ScriptletsSettings> =
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(ScriptletsSettings::class.java)
}
