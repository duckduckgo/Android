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

package com.duckduckgo.remote.messaging.impl.di

import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.api.Content.PromoSingleAction
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.mappers.ActionAdapter
import com.duckduckgo.remote.messaging.impl.mappers.MessageMapper
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object RMFMapperModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesMessageMapper(
        messageAdapter: Lazy<JsonAdapter<RemoteMessage>>,
    ): MessageMapper {
        return MessageMapper(messageAdapter)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMoshiAdapter(
        actionMappers: DaggerSet<MessageActionMapperPlugin>,
    ): JsonAdapter<RemoteMessage> {
        val moshi = Moshi.Builder()
            .add(
                PolymorphicJsonAdapterFactory.of(Content::class.java, "messageType")
                    .withSubtype(Small::class.java, MessageType.SMALL.name)
                    .withSubtype(Medium::class.java, MessageType.MEDIUM.name)
                    .withSubtype(BigSingleAction::class.java, MessageType.BIG_SINGLE_ACTION.name)
                    .withSubtype(BigTwoActions::class.java, MessageType.BIG_TWO_ACTION.name)
                    .withSubtype(PromoSingleAction::class.java, MessageType.PROMO_SINGLE_ACTION.name),
            )
            .add(ActionAdapter(actionMappers))
            .add(KotlinJsonAdapterFactory())
            .build()
        return moshi.adapter(RemoteMessage::class.java)
    }
}
