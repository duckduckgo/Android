/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.events.db

import com.duckduckgo.app.global.events.db.UserEventsPayloadMapper.UserEventPayload.SitePayload
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

class UserEventsPayloadMapper {

    private val sitePayloadMapper = SitePayloadMapper()

    sealed class UserEventPayload {
        data class SitePayload(val url: String, val title: String) : UserEventPayload()
        object Empty : UserEventPayload()
    }

    fun addPayload(userEventEntity: UserEventEntity, payload: UserEventPayload): UserEventEntity {
        return when (userEventEntity.id) {
            UserEventKey.FIRST_NON_SERP_VISITED_SITE -> {
                if (payload !is SitePayload) return userEventEntity
                userEventEntity.copy(payload = sitePayloadMapper.toPayload(payload))
            }
            else -> {
                userEventEntity
            }
        }
    }

    fun getPayload(userEventEntity: UserEventEntity): UserEventPayload {
        return when (userEventEntity.id) {
            UserEventKey.FIRST_NON_SERP_VISITED_SITE -> {
                sitePayloadMapper.fromPayload(userEventEntity.payload)
            }
            else -> {
                UserEventPayload.Empty
            }
        }
    }

    private class SitePayloadMapper {

        fun toPayload(sitePayload: SitePayload): String {
            return payloadAdapter.toJson(sitePayload)
        }

        fun fromPayload(payload: String): UserEventPayload {
            return runCatching {
                payloadAdapter.fromJson(payload) ?: UserEventPayload.Empty
            }.getOrDefault(UserEventPayload.Empty)
        }

        companion object {
            private val moshi = Moshi.Builder().build()
            val payloadAdapter: JsonAdapter<SitePayload> = moshi.adapter(SitePayload::class.java)
        }
    }
}
