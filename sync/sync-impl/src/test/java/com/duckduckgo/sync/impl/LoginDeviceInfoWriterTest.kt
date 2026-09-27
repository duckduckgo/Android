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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class LoginDeviceInfoWriterTest {

    private val accountInfoDdgWrapRepairer: AccountInfoDdgWrapRepairer = mock()
    private val deviceInfoMigrator: DeviceInfoMigrator = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private lateinit var writer: RealLoginDeviceInfoWriter

    @Before
    fun before() {
        writer = RealLoginDeviceInfoWriter(
            syncFeature = syncFeature,
            accountInfoDdgWrapRepairer = accountInfoDdgWrapRepairer,
            deviceInfoMigrator = deviceInfoMigrator,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = true))
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(accountInfoDdgWrapRepairer.repair(any(), any())).thenReturn(Result.Success(Unit))
        runBlocking { whenever(deviceInfoMigrator.ensureMigrated()).thenReturn(Result.Success(Unit)) }
    }

    @Test
    fun whenAccountInfoKeyAbsentThenNoReWrapButStillMigrates() = runTest {
        val result = writer.onLogin(loginResponseKeys = emptyList())

        assertTrue(result is Result.Success)
        verifyNoInteractions(accountInfoDdgWrapRepairer)
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenAccountInfoKeyExistsThenRepairsItsDdgWrapBeforeMigrating() = runTest {
        val entries = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY))

        val result = writer.onLogin(loginResponseKeys = entries)

        assertTrue(result is Result.Success)
        verify(accountInfoDdgWrapRepairer).repair("kid-1", entries)
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenDdgWrapRepairFailsThenStillMigrates() = runTest {
        val entries = listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY))
        whenever(accountInfoDdgWrapRepairer.repair("kid-1", entries)).thenReturn(Result.Error(reason = "repair failed"))

        val result = writer.onLogin(loginResponseKeys = entries)

        assertTrue(result is Result.Success)
        verify(deviceInfoMigrator).ensureMigrated()
    }

    @Test
    fun whenWriteFeatureDisabledThenNothingRuns() = runTest {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))

        val result = writer.onLogin(listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        assertTrue(result is Result.Success)
        verifyNoInteractions(accountInfoDdgWrapRepairer)
        verify(deviceInfoMigrator, never()).ensureMigrated()
    }

    @Test
    fun whenV2ConnectFlowDisabledThenNothingRuns() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(enable = false))

        writer.onLogin(listOf(accountInfoEntry(encryptedWith = CREDENTIAL_ID_3PARTY)))

        verifyNoInteractions(accountInfoDdgWrapRepairer)
        verify(deviceInfoMigrator, never()).ensureMigrated()
    }

    private fun accountInfoEntry(encryptedWith: String) = ProtectedKeyEntry(
        kid = "kid-1",
        purpose = SYNC_PURPOSE_ACCOUNT_INFO,
        encryptedWith = encryptedWith,
        encryptedPrivateKey = "wrapped-private-key",
        publicKey = RsaJwk(n = "n", e = "AQAB"),
    )
}
