/*
 * Copyright (c) 2020 DuckDuckGo
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

import com.duckduckgo.app.global.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface UserEventsStore {
    suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity?
    suspend fun registerUserEvent(userEventKey: UserEventKey)
}

class AppUserEventsStore @Inject constructor(
    private val userEventsDao: UserEventsDao,
    private val dispatcher: DispatcherProvider
) : UserEventsStore {

    override suspend fun getUserEvent(userEventKey: UserEventKey): UserEventEntity? {
        return withContext(dispatcher.io()) {
            userEventsDao.getUserEvent(userEventKey)
        }
    }

    override suspend fun registerUserEvent(userEventKey: UserEventKey) {
        withContext(dispatcher.io()) {
            userEventsDao.insert(UserEventEntity(userEventKey))
        }
    }
}
