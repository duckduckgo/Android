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

package com.duckduckgo.sync.impl.autorestore

import android.annotation.SuppressLint
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class SyncAutoRestoreAccountDisabledObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val syncStore: SyncStore = mock()
    private val syncAutoRestoreManager: SyncAutoRestoreManager = mock()

    private lateinit var testee: SyncAutoRestoreAccountDisabledObserver

    @Before
    fun setup() {
        syncFeature.syncAutoRestore().setRawStoredState(State(enable = true))
        testee = SyncAutoRestoreAccountDisabledObserver(
            appCoroutineScope = coroutineTestRule.testScope,
            syncStore = syncStore,
            syncAutoRestoreManager = syncAutoRestoreManager,
            syncFeature = syncFeature,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenSignedOutThenClearsRecoveryCode() = runTest {
        val isSignedInFlow = MutableStateFlow(true)
        whenever(syncStore.isSignedInFlow()).thenReturn(isSignedInFlow)
        testee.onCreate(mock())

        isSignedInFlow.emit(false)

        verify(syncAutoRestoreManager).clearRecoveryCode()
    }

    @Test
    fun whenSignedInAfterSignOutEventThenDoesNotClearRecoveryCode() = runTest {
        val isSignedInFlow = MutableStateFlow(false)
        whenever(syncStore.isSignedInFlow()).thenReturn(isSignedInFlow)
        testee.onCreate(mock())

        isSignedInFlow.emit(true)

        verify(syncAutoRestoreManager, never()).clearRecoveryCode()
    }

    @Test
    fun whenInitialSignedOutValueWithNoChangesThenDoesNotClearRecoveryCode() = runTest {
        val isSignedInFlow = MutableStateFlow(false)
        whenever(syncStore.isSignedInFlow()).thenReturn(isSignedInFlow)

        testee.onCreate(mock())

        verify(syncAutoRestoreManager, never()).clearRecoveryCode()
    }

    @Test
    fun whenSignedOutButFeatureFlagDisabledThenDoesNotClearRecoveryCode() = runTest {
        syncFeature.syncAutoRestore().setRawStoredState(State(enable = false))
        val isSignedInFlow = MutableStateFlow(true)
        whenever(syncStore.isSignedInFlow()).thenReturn(isSignedInFlow)
        testee.onCreate(mock())

        isSignedInFlow.emit(false)

        verify(syncAutoRestoreManager, never()).clearRecoveryCode()
    }

    @Test
    fun whenSignedOutMultipleTimesThenClearsRecoveryCodeEachTime() = runTest {
        val isSignedInFlow = MutableStateFlow(true)
        whenever(syncStore.isSignedInFlow()).thenReturn(isSignedInFlow)
        testee.onCreate(mock())

        isSignedInFlow.emit(false)
        isSignedInFlow.emit(true)
        isSignedInFlow.emit(false)

        verify(syncAutoRestoreManager, org.mockito.kotlin.times(2)).clearRecoveryCode()
    }
}
