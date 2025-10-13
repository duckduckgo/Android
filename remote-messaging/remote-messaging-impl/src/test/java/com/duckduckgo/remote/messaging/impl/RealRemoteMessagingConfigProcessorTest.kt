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

import android.annotation.SuppressLint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.fixtures.RemoteMessagingConfigOM.aRemoteMessagingConfig
import com.duckduckgo.remote.messaging.fixtures.jsonMatchingAttributeMappers
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.store.RemoteMessagingCohortStore
import com.duckduckgo.remote.messaging.store.RemoteMessagingConfigRepository
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@SuppressLint("DenyListedApi")
class RealRemoteMessagingConfigProcessorTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val remoteMessagingConfigJsonMapper = RemoteMessagingConfigJsonMapper(
        appBuildConfig,
        jsonMatchingAttributeMappers,
        messageActionPlugins,
    )
    private val remoteMessagingConfigRepository = mock<RemoteMessagingConfigRepository>()
    private val remoteMessagingRepository = mock<RemoteMessagingRepository>()
    private val remoteMessagingCohortStore = mock<RemoteMessagingCohortStore>()
    private val remoteMessagingConfigMatcher = RemoteMessagingConfigMatcher(setOf(mock(), mock(), mock()), mock(), remoteMessagingCohortStore)
    private var remoteMessagingFeatureToggles: RemoteMessagingFeatureToggles = FakeFeatureToggleFactory.create(
        RemoteMessagingFeatureToggles::class.java,
    )

    private val testee = RealRemoteMessagingConfigProcessor(
        remoteMessagingConfigJsonMapper,
        remoteMessagingConfigRepository,
        remoteMessagingRepository,
        remoteMessagingConfigMatcher,
        remoteMessagingFeatureToggles,
    )

    @Before
    fun setup() {
        whenever(appBuildConfig.deviceLocale).thenReturn(Locale.US)
    }

    @Test
    fun whenNewVersionThenEvaluate() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(version = 0L),
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }

    @Test
    fun whenSameVersionThenDoNothingIfAlwaysProcessFFDisabled() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(false))
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(
                version = 1L,
                evaluationTimestamp = dateTimeFormatter.format(LocalDateTime.now()),
            ),
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository, times(0)).insert(any())
    }

    @Test
    fun whenSameVersionThenProcessIfAlwaysProcessFFEnabled() = runTest {
        remoteMessagingFeatureToggles.alwaysProcessRemoteConfig().setRawStoredState(State(true))
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(
                version = 1L,
                evaluationTimestamp = dateTimeFormatter.format(LocalDateTime.now()),
            ),
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }

    @Test
    fun whenSameVersionButExpiredThenEvaluate() = runTest {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(
                version = 0L,
                evaluationTimestamp = dateTimeFormatter.format(LocalDateTime.now().minusDays(2L)),
            ),
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }

    @Test
    fun whenSameVersionButInvalidatedThenEvaluate() = runTest {
        whenever(remoteMessagingConfigRepository.get()).thenReturn(
            aRemoteMessagingConfig(
                version = 1L,
                invalidate = true,
            ),
        )

        testee.process(aJsonRemoteMessagingConfig(version = 1L))

        verify(remoteMessagingConfigRepository).insert(any())
    }
}
