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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.fixtures.RemoteMessagingConfigOM.aRemoteMessagingConfig
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RealRemoteMessagingConfigProcessorTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val remoteMessagingConfigJsonMapper = mock<RemoteMessagingConfigJsonMapper>()
    private val remoteMessagingConfigRepository = mock<RemoteMessagingConfigRepository>()
    private val remoteMessagingRepository = mock<RemoteMessagingRepository>()
    private val remoteMessagingConfigMatcher = mock<RemoteMessagingConfigMatcher>()

    private val testee = RealRemoteMessagingConfigProcessor(
        remoteMessagingConfigJsonMapper, remoteMessagingConfigRepository, remoteMessagingRepository, remoteMessagingConfigMatcher
    )

    @Test
    fun whenNewVersionThenEvaluate() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(version = 0L)
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }

    @Test
    fun whenSameVersionThenDoNothing() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(version = 1L)
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository, times(0)).insert(any())
    }

    @Test
    fun whenSameVersionButExpiredThenEvaluate() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(version = 0L)
        )
        whenever(remoteMessagingConfigRepository.expired()).thenReturn(true)

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }

    @Test
    fun whenSameVersionButInvalidatedThenEvaluate() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(version = 0L)
        )
        whenever(remoteMessagingConfigRepository.invalidated()).thenReturn(true)

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }
}
