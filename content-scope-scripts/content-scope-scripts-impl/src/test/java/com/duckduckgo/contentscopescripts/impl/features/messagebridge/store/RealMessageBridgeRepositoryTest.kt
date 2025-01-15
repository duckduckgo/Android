/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.features.messagebridge.store

import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealMessageBridgeRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealMessageBridgeRepository

    private val mockDatabase: MessageBridgeDatabase = mock()
    private val mockMessageBridgeDao: MessageBridgeDao = mock()

    @Before
    fun before() {
        whenever(mockMessageBridgeDao.get()).thenReturn(null)
        whenever(mockDatabase.messageBridgeDao()).thenReturn(mockMessageBridgeDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealMessageBridgeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            verify(mockMessageBridgeDao).get()
            assertEquals("{}", testee.messageBridgeEntity.json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockMessageBridgeDao.get()).thenReturn(messageBridgeEntity)
            testee =
                RealMessageBridgeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            verify(mockMessageBridgeDao).get()
            assertEquals(messageBridgeEntity.json, testee.messageBridgeEntity.json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealMessageBridgeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )

            testee.updateAll(messageBridgeEntity)

            verify(mockMessageBridgeDao).updateAll(messageBridgeEntity)
        }

    companion object {
        val messageBridgeEntity = MessageBridgeEntity(json = "{\"key\":\"value\"}")
    }
}
