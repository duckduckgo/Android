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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.decryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.deviceFactor
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceName
import com.duckduckgo.sync.TestSyncFixtures.deviceType
import com.duckduckgo.sync.TestSyncFixtures.getDevicesError
import com.duckduckgo.sync.TestSyncFixtures.getDevicesSuccess
import com.duckduckgo.sync.TestSyncFixtures.listOfConnectedDevices
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.protectedEncryptionKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.stretchedPrimaryKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RealConnectedDevicesRepositoryTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncDeviceIds: SyncDeviceIds = mock()
    private val nativeLib: SyncLib = mock()
    private val syncApi: SyncApi = mock()
    private val syncStore: SyncStore = mock()
    private val syncPixels: SyncPixels = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val thirdPartyDeviceListDecryptor: ThirdPartyDeviceListDecryptor = mock()
    private val deviceInfoUpdater: DeviceInfoUpdater = mock()
    private val publishTracker = DeviceInfoPublishWatcher()
    private val accountInfoDdgWrapRepairer: AccountInfoDdgWrapRepairer = mock()
    private val remoteDeviceLogout: RemoteDeviceLogout = mock()

    private lateinit var testee: RealConnectedDevicesRepository

    @Before
    fun before() {
        testee = RealConnectedDevicesRepository(
            syncDeviceIds = syncDeviceIds,
            nativeLib = nativeLib,
            syncApi = syncApi,
            syncStore = syncStore,
            syncPixels = syncPixels,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            syncFeature = syncFeature,
            thirdPartyDeviceListDecryptor = thirdPartyDeviceListDecryptor,
            deviceInfoUpdater = deviceInfoUpdater,
            deviceInfoPublishWatcher = publishTracker,
            accountInfoDdgWrapRepairer = accountInfoDdgWrapRepairer,
            remoteDeviceLogout = remoteDeviceLogout,
        )

        whenever(accountInfoDdgWrapRepairer.repair(any(), anyOrNull())).thenReturn(Success(Unit))
    }

    @Test
    fun getConnectedDevicesSucceedsThenReturnSuccess() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        val result = testee.getConnectedDevices() as Success

        assertEquals(listOfConnectedDevices, result.data)
    }

    @Test
    fun getConnectedDevicesSucceedsThenUpdateDevicesCount() {
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        val result = testee.getConnectedDevices() as Success

        verify(syncStore).connectedDevicesCount = result.data.size
    }

    @Test
    fun getConnectedDevicesReturnsListWithLocalDeviceInFirstPosition() {
        givenAuthenticatedDevice()
        prepareForEncryption()
        val thisDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherDevice = Device(deviceId = "anotherDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherRemoteDevice = Device(deviceId = "anotherRemoteDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = listOf(anotherDevice, anotherRemoteDevice, thisDevice), entriesV2 = null)),
        )

        val result = testee.getConnectedDevices() as Success

        assertTrue(result.data.first().thisDevice)
    }

    @Test
    fun getConnectedDevicesKeepsServerOrderOfOtherDevices() {
        givenAuthenticatedDevice()
        prepareForEncryption()
        val thisDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherDevice = Device(deviceId = "anotherDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherRemoteDevice = Device(deviceId = "anotherRemoteDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = listOf(anotherDevice, thisDevice, anotherRemoteDevice), entriesV2 = null)),
        )

        val result = testee.getConnectedDevices() as Success

        assertEquals(listOf(deviceId, "anotherDeviceId", "anotherRemoteDeviceId"), result.data.map { it.deviceId })
    }

    @Test
    fun getConnectedDevicesFailsThenReturnServerErrorCode() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesError)

        val result = testee.getConnectedDevices() as Error

        assertEquals(getDevicesError.code, result.code)
    }

    @Test
    fun latestConnectedDeviceCountFlowReturnsStoredCount() = runTest {
        whenever(syncStore.connectedDevicesCountFlow()).thenReturn(flowOf(3))

        assertEquals(3, testee.latestConnectedDeviceCountFlow().first())
    }

    @Test
    fun latestConnectedDeviceCountReturnsStoredCount() = runTest {
        whenever(syncStore.connectedDevicesCount).thenReturn(3)

        assertEquals(3, testee.latestConnectedDeviceCount())
    }

    @Test
    fun getConnectedDevicesRejectedByServerThenKeepDevicesCount() {
        givenDeviceListRequest()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesError)

        testee.getConnectedDevices()

        verify(syncStore, never()).connectedDevicesCount = any()
    }

    @Test
    fun getConnectedDevicesFailsWithoutServerResponseThenKeepDevicesCount() {
        givenDeviceListRequest()
        whenever(syncApi.getDevices(anyString())).thenReturn(Error(reason = "timeout"))

        testee.getConnectedDevices()

        verify(syncStore, never()).connectedDevicesCount = any()
    }

    @Test
    fun whenFetchCompletesAfterSignOutThenDevicesCountNotUpdated() {
        givenDeviceListRequest()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)
        whenever(syncStore.userId).thenReturn(userId, null)

        testee.getConnectedDevices()

        verify(syncStore, never()).connectedDevicesCount = any()
    }

    @Test
    fun whenFetchCompletesAfterAccountSwitchThenDevicesCountNotUpdated() {
        givenDeviceListRequest()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)
        whenever(syncStore.userId).thenReturn(userId, "anotherUserId")

        testee.getConnectedDevices()

        verify(syncStore, never()).connectedDevicesCount = any()
    }

    @Test
    fun getConnectedDevicesDecryptionFailsThenLogoutDevice() {
        givenAuthenticatedDevice()
        prepareForEncryption()
        val thisDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val otherDevice = Device(deviceId = "otherDeviceId", deviceName = "otherDeviceName", jwIat = "", deviceType = "otherDeviceType")
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(nativeLib.decryptData(anyString(), anyString())).thenThrow(NegativeArraySizeException())
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = listOf(thisDevice, otherDevice), entriesV2 = null)),
        )

        val result = testee.getConnectedDevices() as Success

        verify(remoteDeviceLogout).logout("otherDeviceId")
        verify(remoteDeviceLogout, never()).logout(deviceId)
        assertTrue(result.data.isEmpty())
    }

    // ----- entries_v2 device-list path (Track C) ----------------------------------------------

    @Test
    fun whenV2FlagOnAndEntriesV2PresentThenUsesV2Decryptor() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val v2Entry = DeviceV2(deviceId = "d1", deviceName = "ENC", deviceType = "ENC_T", credentialId = "3party")
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = emptyList(), entriesV2 = listOf(v2Entry))),
        )
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(v2Entry), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId = "d1", name = "Chrome/148", type = "Browser")),
                undecryptable = emptyList(),
                ownDeviceReadOutcome = OwnDeviceReadOutcome.ResolvedDeviceInfo,
            ),
        )

        val result = testee.getConnectedDevices() as Success

        assertEquals(1, result.data.size)
        assertEquals("Chrome/148", result.data[0].deviceName)
        verify(thirdPartyDeviceListDecryptor).decryptAll(listOf(v2Entry), deviceId, 0)
        verify(syncPixels).fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.OwnRowResolvedDeviceInfo)
        verifyNoInteractions(remoteDeviceLogout)
    }

    @Test
    fun whenDeviceInfoPublishedDuringTheDevicesRequestThenDecryptorGetsThePrePublishSnapshot() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val v2Entry = DeviceV2(deviceId = "d1", deviceName = "ENC", deviceType = "ENC_T", credentialId = "ddg")
        val snapshotBefore = publishTracker.snapshot()
        whenever(syncApi.getDevices(anyString())).thenAnswer {
            publishTracker.markPublished()
            Success(DeviceEntries(entries = emptyList(), entriesV2 = listOf(v2Entry)))
        }
        whenever(thirdPartyDeviceListDecryptor.decryptAll(any(), anyOrNull(), any())).thenReturn(
            DecryptAllResult(decrypted = emptyList(), undecryptable = emptyList()),
        )

        testee.getConnectedDevices()

        verify(thirdPartyDeviceListDecryptor).decryptAll(listOf(v2Entry), deviceId, snapshotBefore)
    }

    @Test
    fun whenOwnRowFallsBackWithReadAndWriteEnabledThenLegacyPixelFires() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val own = DeviceV2(deviceId = deviceId, credentialId = "ddg")
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(DeviceEntries(emptyList(), listOf(own))))
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(own), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId, deviceName, "phone")),
                undecryptable = emptyList(),
                ownDeviceReadOutcome = OwnDeviceReadOutcome.ResolvedLegacy(DeviceInfoReadFailureReason.BLOB_ABSENT),
            ),
        )

        testee.getConnectedDevices()

        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.OwnRowResolvedLegacy(DeviceInfoReadFailureReason.BLOB_ABSENT),
        )
    }

    @Test
    fun whenOwnRowFallsBackWithWriteDisabledThenLegacyPixelDoesNotFire() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(false))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val own = DeviceV2(deviceId = deviceId, credentialId = "ddg")
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(DeviceEntries(emptyList(), listOf(own))))
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(own), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId, deviceName, "phone")),
                undecryptable = emptyList(),
                ownDeviceReadOutcome = OwnDeviceReadOutcome.ResolvedLegacy(DeviceInfoReadFailureReason.BLOB_ABSENT),
            ),
        )

        testee.getConnectedDevices()

        verify(syncPixels, never()).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.OwnRowResolvedLegacy(DeviceInfoReadFailureReason.BLOB_ABSENT),
        )
    }

    @Test
    fun whenKeyIsUnavailableThenOnlyKeyUnavailablePixelFires() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val own = DeviceV2(deviceId = deviceId, credentialId = "ddg")
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(DeviceEntries(emptyList(), listOf(own))))
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(own), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId, deviceName, "phone")),
                undecryptable = emptyList(),
                keyUnavailableReason = AccountInfoKeyUnavailableReason.RATE_LIMITED,
            ),
        )

        testee.getConnectedDevices()

        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyUnavailable(AccountInfoKeyUnavailableReason.RATE_LIMITED),
        )
        verifyNoInteractions(accountInfoDdgWrapRepairer)
    }

    @Test
    fun whenDdgWrapIsUnavailableThenRepairsCachedAccountInfoKey() = runTest {
        givenAccountInfoDdgWrapUnavailable()

        testee.getConnectedDevices()

        verify(accountInfoDdgWrapRepairer).repair("account-info-kid")
    }

    @Test
    fun whenDdgWrapRepairSucceedsThenDoesNotRepairAgainForSameAccount() = runTest {
        givenAccountInfoDdgWrapUnavailable()

        testee.getConnectedDevices()
        testee.getConnectedDevices()

        verify(accountInfoDdgWrapRepairer, times(1)).repair("account-info-kid")
    }

    @Test
    fun whenDdgWrapRepairFailsThenRetriesOnNextDeviceListRead() = runTest {
        givenAccountInfoDdgWrapUnavailable()
        whenever(accountInfoDdgWrapRepairer.repair("account-info-kid")).thenReturn(Error(reason = "repair failed"))

        testee.getConnectedDevices()
        testee.getConnectedDevices()

        verify(accountInfoDdgWrapRepairer, times(2)).repair("account-info-kid")
    }

    @Test
    fun whenOtherRowDecryptFailsToPlaceholderThenBothPixelsFire() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val other = DeviceV2(deviceId = "other", credentialId = "3party")
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(DeviceEntries(emptyList(), listOf(other))))
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(other), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice("other", "Unknown device", "Browser")),
                undecryptable = emptyList(),
                otherRowFailedDecryptionCredentials = setOf(DeviceCredential.THIRD_PARTY),
                otherRowPlaceholderCredentials = setOf(DeviceCredential.THIRD_PARTY),
            ),
        )

        testee.getConnectedDevices()

        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.OtherRowDeviceInfoFailedDecryption(DeviceCredential.THIRD_PARTY),
        )
        verify(syncPixels).fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.OtherRowResolvedPlaceholder(DeviceCredential.THIRD_PARTY),
        )
    }

    @Test
    fun whenV2FlagOnAndEntriesV2NullThenFallsBackToLegacyDecryptOfEntries() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        givenAuthenticatedDevice()
        prepareForEncryption()
        val ddgDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = listOf(ddgDevice), entriesV2 = null)),
        )

        val result = testee.getConnectedDevices() as Success

        // Fell back to legacy decrypt — same library path as the legacy `entries`-only response.
        assertEquals(1, result.data.size)
        verify(thirdPartyDeviceListDecryptor, never()).decryptAll(any(), anyOrNull(), any())
    }

    @Test
    fun whenV2FlagOnAndDeviceListApiFailsThenReturnsGenericError() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncApi.getDevices(anyString())).thenReturn(Error(reason = "boom"))

        val result = testee.getConnectedDevices() as Error

        assertEquals(GENERIC_ERROR.code, result.code)
    }

    @Test
    fun whenV2FlagOnAndDecryptorReportsLogoutThenLogoutIsCalledForOtherDevices() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val v2Entry = DeviceV2(deviceId = "d-other", deviceName = "BAD", credentialId = "3party")
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = emptyList(), entriesV2 = listOf(v2Entry))),
        )
        whenever(thirdPartyDeviceListDecryptor.decryptAll(any(), anyOrNull(), any())).thenReturn(
            DecryptAllResult(decrypted = emptyList(), undecryptable = listOf("d-other")),
        )

        val result = testee.getConnectedDevices() as Success

        assertTrue(result.data.isEmpty())
        verify(remoteDeviceLogout).logout("d-other")
    }

    @Test
    fun whenV2FlagOnAndDecryptFailsForThisDeviceThenDoesNotLogoutSelf() {
        // Safety: never log out the local device, even if decrypt fails for it.
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        val v2Entry = DeviceV2(deviceId = deviceId, deviceName = "BAD", credentialId = "3party")
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = emptyList(), entriesV2 = listOf(v2Entry))),
        )
        whenever(thirdPartyDeviceListDecryptor.decryptAll(any(), anyOrNull(), any())).thenReturn(
            DecryptAllResult(decrypted = emptyList(), undecryptable = listOf(deviceId)),
        )

        testee.getConnectedDevices()

        verifyNoInteractions(remoteDeviceLogout)
    }

    @Test
    fun whenV2FlagOffThenLegacyPathUntouchedAndV2DecryptorNotCalled() {
        // Sanity: with flag off, behaviour matches pre-Track-C.
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        testee.getConnectedDevices()

        verify(thirdPartyDeviceListDecryptor, never()).decryptAll(any(), anyOrNull(), any())
    }

    @Test
    fun whenThisDeviceInfoUnresolvedThenRepublishesItWithTheCurrentName() = runTest {
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(deviceInfoUpdater.setThisDeviceName(name = any(), source = eq(DeviceInfoUpdateSource.REPAIR))).thenReturn(Success(emptyList()))

        testee.getConnectedDevices()

        verify(deviceInfoUpdater).setThisDeviceName(name = deviceName, source = DeviceInfoUpdateSource.REPAIR)
    }

    @Test
    fun whenThisDeviceInfoUnresolvedOnEveryRenderThenRepublishesOnlyOncePerProcess() = runTest {
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(deviceInfoUpdater.setThisDeviceName(name = any(), source = eq(DeviceInfoUpdateSource.REPAIR))).thenReturn(Success(emptyList()))

        testee.getConnectedDevices()
        testee.getConnectedDevices()

        verify(deviceInfoUpdater, times(1)).setThisDeviceName(name = anyString(), source = eq(DeviceInfoUpdateSource.REPAIR))
    }

    @Test
    fun whenRepublishFailsThenRetriesOnTheNextRender() = runTest {
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(
            deviceInfoUpdater.setThisDeviceName(name = any(), source = eq(DeviceInfoUpdateSource.REPAIR)),
        ).thenReturn(Error(reason = "no network"))

        testee.getConnectedDevices()
        testee.getConnectedDevices()

        verify(deviceInfoUpdater, times(2)).setThisDeviceName(name = anyString(), source = eq(DeviceInfoUpdateSource.REPAIR))
    }

    @Test
    fun whenSignedIntoAnotherAccountInTheSameProcessThenRepublishesAgain() = runTest {
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(deviceInfoUpdater.setThisDeviceName(name = any(), source = eq(DeviceInfoUpdateSource.REPAIR))).thenReturn(Success(emptyList()))

        testee.getConnectedDevices()
        whenever(syncStore.userId).thenReturn("anotherUserId")
        testee.getConnectedDevices()

        verify(deviceInfoUpdater, times(2)).setThisDeviceName(name = anyString(), source = eq(DeviceInfoUpdateSource.REPAIR))
    }

    @Test
    fun whenNotSignedInThenDoesNotRepublish() = runTest {
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
        whenever(syncStore.userId).thenReturn(null)

        testee.getConnectedDevices()

        verifyNoInteractions(deviceInfoUpdater)
    }

    @Test
    fun whenThisDeviceInfoUnresolvedButWritingDeviceInfoDisabledThenDoesNotRepublish() = runTest {
        // the PATCH would omit device_info and the backend would clear it, deleting the blob we're repairing
        givenThisDeviceInfoUnresolvedOnRead()
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))

        testee.getConnectedDevices()

        verifyNoInteractions(deviceInfoUpdater)
    }

    @Test
    fun whenThisDeviceInfoResolvedThenDoesNotRepublish() = runTest {
        givenThisDeviceInfoUnresolvedOnRead(unresolved = false)
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))

        testee.getConnectedDevices()

        verifyNoInteractions(deviceInfoUpdater)
    }

    private fun givenThisDeviceInfoUnresolvedOnRead(unresolved: Boolean = true) {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        val ownEntry = DeviceV2(deviceId = deviceId, deviceName = "ENC", credentialId = "ddg")
        whenever(syncApi.getDevices(anyString())).thenReturn(
            Success(DeviceEntries(entries = emptyList(), entriesV2 = listOf(ownEntry))),
        )
        whenever(thirdPartyDeviceListDecryptor.decryptAll(any(), anyOrNull(), any())).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId = deviceId, name = deviceName, type = "phone")),
                undecryptable = emptyList(),
                thisDeviceInfoNeedsRepair = unresolved,
            ),
        )
    }

    private fun givenAccountInfoDdgWrapUnavailable() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canReadUnifiedDeviceList().setRawStoredState(State(true))
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.accountInfoPublicKey).thenReturn(
            AccountInfoPublicKey(keyId = "account-info-kid", modulus = "modulus", exponent = "AQAB"),
        )
        val own = DeviceV2(deviceId = deviceId, credentialId = CREDENTIAL_ID_DDG)
        whenever(syncApi.getDevices(token)).thenReturn(Success(DeviceEntries(emptyList(), listOf(own))))
        whenever(thirdPartyDeviceListDecryptor.decryptAll(listOf(own), deviceId, 0)).thenReturn(
            DecryptAllResult(
                decrypted = listOf(DecryptedDevice(deviceId, deviceName, "phone")),
                undecryptable = emptyList(),
                keyUnavailableReason = AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL,
            ),
        )
    }

    private fun givenDeviceListRequest() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
    }

    private fun givenAuthenticatedDevice() {
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.deviceName).thenReturn(deviceName)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.isSignedIn()).thenReturn(true)
    }

    private fun prepareForEncryption() {
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(decryptedSecretKey)
        whenever(nativeLib.decryptData(anyString(), primaryKey = eq(primaryKey))).thenAnswer {
            DecryptResult(0, it.arguments.first() as String)
        }
        whenever(nativeLib.encryptData(anyString(), primaryKey = eq(primaryKey))).thenAnswer {
            EncryptResult(0, it.arguments.first() as String)
        }
    }
}
