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

package com.duckduckgo.sync.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures.aDevice
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailDupUser
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedSuccess
import com.duckduckgo.sync.TestSyncFixtures.accountKeys
import com.duckduckgo.sync.TestSyncFixtures.accountKeysFailed
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceKeysGoneError
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceKeysNotFoundError
import com.duckduckgo.sync.TestSyncFixtures.connectDeviceSuccess
import com.duckduckgo.sync.TestSyncFixtures.connectKeys
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.decryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.deleteAccountSuccess
import com.duckduckgo.sync.TestSyncFixtures.deviceFactor
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.deviceName
import com.duckduckgo.sync.TestSyncFixtures.deviceType
import com.duckduckgo.sync.TestSyncFixtures.encryptedExchangeCode
import com.duckduckgo.sync.TestSyncFixtures.encryptedRecoveryCode
import com.duckduckgo.sync.TestSyncFixtures.failedLoginKeys
import com.duckduckgo.sync.TestSyncFixtures.getDevicesError
import com.duckduckgo.sync.TestSyncFixtures.getDevicesSuccess
import com.duckduckgo.sync.TestSyncFixtures.hashedPassword
import com.duckduckgo.sync.TestSyncFixtures.invalidDecryptedSecretKey
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonExchangeKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.listOfConnectedDevices
import com.duckduckgo.sync.TestSyncFixtures.loginFailed
import com.duckduckgo.sync.TestSyncFixtures.loginSuccess
import com.duckduckgo.sync.TestSyncFixtures.logoutSuccess
import com.duckduckgo.sync.TestSyncFixtures.otherDeviceKeyId
import com.duckduckgo.sync.TestSyncFixtures.primaryDeviceKeyId
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.protectedEncryptionKey
import com.duckduckgo.sync.TestSyncFixtures.secretKey
import com.duckduckgo.sync.TestSyncFixtures.stretchedPrimaryKey
import com.duckduckgo.sync.TestSyncFixtures.token
import com.duckduckgo.sync.TestSyncFixtures.userId
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.metrics.ConnectedDevicesObserver
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrlWrapper
import com.duckduckgo.sync.store.SyncStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class AppSyncAccountRepositoryTest {

    private var nativeLib: SyncLib = mock()
    private var syncDeviceIds: SyncDeviceIds = mock()
    private var syncApi: SyncApi = mock()
    private var syncStore: SyncStore = mock()
    private var syncEngine: SyncEngine = mock()
    private var syncPixels: SyncPixels = mock()
    private val deviceKeyGenerator: DeviceKeyGenerator = mock()
    private val connectedDevicesObserver: ConnectedDevicesObserver = mock()
    private val moshi = Moshi.Builder().build()
    private val invitationCodeWrapperAdapter = moshi.adapter(InvitationCodeWrapper::class.java)
    private val invitedDeviceDetailsAdapter = moshi.adapter(InvitedDeviceDetails::class.java)
    private val recoveryCodeAdapter = moshi.adapter(LinkCode::class.java)
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
        this.seamlessAccountSwitching().setRawStoredState(State(true))
    }

    private lateinit var syncRepo: SyncAccountRepository

    private val syncCodeUrlWrapper: SyncBarcodeUrlWrapper = mock()

    @Before
    fun before() {
        syncRepo = AppSyncAccountRepository(
            connectedDevicesObserver,
            syncDeviceIds,
            nativeLib,
            syncApi,
            syncStore,
            syncEngine,
            syncPixels,
            TestScope(),
            DefaultDispatcherProvider(),
            syncFeature,
            deviceKeyGenerator,
            syncCodeUrlWrapper = syncCodeUrlWrapper,
        )

        // passthrough by default (no modifications)
        whenever(syncCodeUrlWrapper.wrapCodeInUrl(any())).thenAnswer { it.arguments[0] as String }
    }

    @Test
    fun whenCreateAccountSucceedsThenAccountPersisted() {
        prepareToProvideDeviceIds()
        prepareForCreateAccountSuccess()

        val result = syncRepo.createAccount()

        assertEquals(Success(true), result)
        verify(syncStore).storeCredentials(
            userId = userId,
            deviceId = deviceId,
            deviceName = deviceName,
            primaryKey = primaryKey,
            secretKey = secretKey,
            token = token,
        )
    }

    @Test
    fun whenUserSignedInCreatesAccountThenReturnAlreadySignedInError() {
        whenever(syncStore.isSignedIn()).thenReturn(true)

        val result = syncRepo.createAccount() as Error

        assertEquals(ALREADY_SIGNED_IN.code, result.code)
    }

    @Test
    fun whenCreateAccountFailsThenReturnCreateAccountError() {
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(accountCreatedFailDupUser)

        val result = syncRepo.createAccount() as Error

        assertEquals(CREATE_ACCOUNT_FAILED.code, result.code)
    }

    @Test
    fun whenCreateAccountGenerateKeysFailsThenReturnCreateAccountError() {
        prepareToProvideDeviceIds()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeysFailed)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(accountCreatedSuccess)

        val result = syncRepo.createAccount() as Error

        assertEquals(CREATE_ACCOUNT_FAILED.code, result.code)
        verifyNoInteractions(syncApi)
    }

    @Test
    fun whenAccountExistsThenGetAccountInfoReturnData() {
        givenAuthenticatedDevice()

        val result = syncRepo.getAccountInfo()

        assertEquals(userId, result.userId)
        assertEquals(deviceId, result.deviceId)
        assertEquals(deviceName, result.deviceName)
        assertTrue(result.isSignedIn)
    }

    @Test
    fun whenAccountNotCreatedThenAccountInfoEmpty() {
        whenever(syncStore.primaryKey).thenReturn("")

        val result = syncRepo.getAccountInfo()

        assertEquals("", result.userId)
        assertEquals("", result.deviceId)
        assertEquals("", result.deviceName)
        assertFalse(result.isSignedIn)
    }

    @Test
    fun whenLogoutSucceedsThenReturnSuccessAndRemoveData() {
        givenAuthenticatedDevice()
        whenever(syncApi.logout(token, deviceId)).thenReturn(logoutSuccess)

        val result = syncRepo.logout(deviceId)

        assertTrue(result is Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenLogoutRemoteDeviceSucceedsThenReturnSuccessButDoNotRemoveLocalData() {
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncApi.logout(eq(token), anyString())).thenReturn(logoutSuccess)

        val result = syncRepo.logout("randomDeviceId")

        assertTrue(result is Success)
        verify(syncStore, times(0)).clearAll()
    }

    @Test
    fun whenDeleteAccountSucceedsThenReturnSuccessAndRemoveData() {
        givenAuthenticatedDevice()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)

        val result = syncRepo.deleteAccount()

        assertTrue(result is Success)
        verify(syncStore).clearAll()
    }

    @Test
    fun whenProcessJsonRecoveryCodeSucceedsThenAccountPersisted() {
        prepareForLoginSuccess()

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded))

        assertEquals(Success(true), result)
        verify(syncStore).storeCredentials(
            userId = userId,
            deviceId = deviceId,
            deviceName = deviceName,
            primaryKey = primaryKey,
            secretKey = secretKey,
            token = token,
        )
    }

    @Test
    fun whenExchangeCodeProcessedButFeatureFlagIsDisabledThenIsError() {
        prepareForExchangeSuccess()
        syncFeature.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(false))

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))
        assertTrue(result is Error)
    }

    @Test
    fun whenExchangeCodeProcessedThenInvitationAccepted() {
        prepareForExchangeSuccess()

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        assertTrue(result is Success)
        verify(syncApi).sendEncryptedMessage(eq(primaryDeviceKeyId), eq(encryptedExchangeCode))
    }

    @Test
    fun whenExchangeCodeProcessedAndInvitationAcceptedRequestFailedThenIsError() {
        syncFeature.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(true))
        prepareForExchangeSuccess()

        whenever(syncApi.sendEncryptedMessage(eq(primaryDeviceKeyId), eq(encryptedExchangeCode))).thenReturn(Result.Error(reason = "error"))
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        assertTrue(result is Error)
    }

    @Test
    fun whenGettingExchangeCodeThenFormatIsCorrect() {
        prepareForExchangeSuccess()
        whenever(deviceKeyGenerator.generate()).thenReturn(primaryDeviceKeyId)
        val resultJson = syncRepo.generateExchangeInvitationCode()
        val result = parseInvitationCodeJson(resultJson)
        assertEquals(validLoginKeys.primaryKey, result.exchangeKey.publicKey)
        assertEquals(primaryDeviceKeyId, result.exchangeKey.keyId)
    }

    @Test
    fun whenAttemptingPollForOtherDeviceExchangeBeforeInvitationCodeGeneratedThenIsError() {
        prepareForExchangeSuccess()
        val result = syncRepo.pollSecondDeviceExchangeAcknowledgement()
        assert(result is Error)
    }

    @Test
    fun whenPollingForOtherDeviceExchangeAndResponseReceivedThenRecoveryCodeSentSuccessfully() {
        givenAuthenticatedDevice()
        prepareForExchangeSuccess()
        initiateInvitationAsPrimaryDevice()

        val result = syncRepo.pollSecondDeviceExchangeAcknowledgement()
        assertTrue(result is Success)
    }

    @Test
    fun whenAttemptingToPollForRecoveryCodeBeforePendingInviteReceivedThenIsError() {
        val result = syncRepo.pollForRecoveryCodeAndLogin()
        assertTrue(result is Error)
    }

    @Test
    fun whenPollingForRecoveryCodeReturnsUnexpectedResponseThenIsError() {
        prepareForExchangeSuccess()

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)
        syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        whenever(syncApi.getEncryptedMessage(otherDeviceKeyId)).thenReturn(Success("encryptedExchangeResponse"))
        whenever(nativeLib.sealOpen("encryptedExchangeResponse", primaryKey, secretKey)).thenReturn("invalid response")

        assertTrue(syncRepo.pollForRecoveryCodeAndLogin() is Error)
    }

    @Test
    fun whenPollingForRecoveryCodeSuccessfulAndNotAlreadySignedInThenIsLoggedIn() {
        prepareForExchangeSuccess()

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)
        syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        configureExchangeResultRecoveryReceived()

        prepareForLoginSuccess()
        val result = syncRepo.pollForRecoveryCodeAndLogin()
        assertTrue(result.getOrNull() is ExchangeResult.LoggedIn)
    }

    @Test
    fun whenPollingForRecoveryCodeSuccessfulAndAlreadySignedInSingleDeviceThenIsLoggedIn() {
        prepareForExchangeSuccess()

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)
        syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        configureExchangeResultRecoveryReceived()
        prepareForLoginSuccess()
        configureAsSignedWithConnectedDevices(numberOfDevices = 1)

        val result = syncRepo.pollForRecoveryCodeAndLogin()
        assertTrue(result.getOrNull() is ExchangeResult.LoggedIn)
    }

    @Test
    fun whenPollingForRecoveryCodeSuccessfulAndAlreadySignedInMultipleDevicesThenAccountSwitchingRequired() {
        prepareForExchangeSuccess()

        val exchangeCode = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        whenever(deviceKeyGenerator.generate()).thenReturn(otherDeviceKeyId)
        syncRepo.processCode(syncRepo.parseSyncAuthCode(exchangeCode.encodeB64()))

        configureExchangeResultRecoveryReceived()
        prepareForLoginSuccess()
        configureAsSignedWithConnectedDevices(numberOfDevices = 2)

        val result = syncRepo.pollForRecoveryCodeAndLogin()
        assertTrue(result.getOrNull() is ExchangeResult.AccountSwitchingRequired)
    }

    private fun configureAsSignedWithConnectedDevices(numberOfDevices: Int) {
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        val devices = mutableListOf<Device>()
        for (i in 1..numberOfDevices) {
            devices.add(Device(deviceId = "$deviceId-$i", deviceName = "$deviceName-$i", jwIat = "", deviceType = deviceFactor))
        }
        whenever(syncApi.getDevices(token)).thenReturn(Success(devices))
        whenever(syncApi.logout(token, deviceId)).thenReturn(Success(Logout(deviceId)))
        syncRepo.getConnectedDevices()
    }

    private fun configureExchangeResultRecoveryReceived() {
        val recoveryCodeJson = recoveryCodeAdapter.toJson((LinkCode(recovery = RecoveryCode(primaryKey, "userId"))))
        whenever(syncApi.getEncryptedMessage(otherDeviceKeyId)).thenReturn(Success("encryptedExchangeResponse"))
        whenever(nativeLib.sealOpen("encryptedExchangeResponse", primaryKey, secretKey)).thenReturn(recoveryCodeJson)
    }

    @Test
    fun whenCodeIsEmptyThenCodeTypeIsUnknown() {
        val type = syncRepo.parseSyncAuthCode("")
        assertTrue(type is SyncAuthCode.Unknown)
    }

    @Test
    fun whenCodeIsRecoveryThenCodeTypeIsIdentified() {
        val code = recoveryCodeAdapter.toJson((LinkCode(recovery = RecoveryCode(primaryKey, "userId"))))
        val type = syncRepo.parseSyncAuthCode(code.encodeB64())
        assertTrue(type is SyncAuthCode.Recovery)
    }

    @Test
    fun whenCodeIsConnectThenCodeTypeIsIdentified() {
        val code = recoveryCodeAdapter.toJson((LinkCode(connect = ConnectCode(deviceId, secretKey))))
        val type = syncRepo.parseSyncAuthCode(code.encodeB64())
        assertTrue(type is SyncAuthCode.Connect)
    }

    @Test
    fun whenCodeIsExchangeThenCodeTypeIsIdentified() {
        val invitationCode = InvitationCode(keyId = primaryDeviceKeyId, publicKey = validLoginKeys.primaryKey)
        val code = invitationCodeWrapperAdapter.toJson(InvitationCodeWrapper(exchangeKey = invitationCode))
        val type = syncRepo.parseSyncAuthCode(code.encodeB64())
        assertTrue(type is SyncAuthCode.Exchange)
    }

    @Test
    fun whenCodeIsUrlWithConnectInsideThenCodeTypeIsIdentified() {
        val code = recoveryCodeAdapter.toJson((LinkCode(connect = ConnectCode(deviceId, secretKey))))
        val webSafeCode = code.encodeB64().applyUrlSafetyFromB64()
        val url = SyncBarcodeUrl(webSafeCode).asUrl()
        val type = syncRepo.parseSyncAuthCode(url)
        assertTrue(type is SyncAuthCode.Connect)
    }

    @Test
    fun whenCodeIsUrlWithExchangeInsideThenCodeTypeIsIdentified() {
        val invitationCode = InvitationCode(keyId = primaryDeviceKeyId, publicKey = validLoginKeys.primaryKey)
        val code = invitationCodeWrapperAdapter.toJson(InvitationCodeWrapper(exchangeKey = invitationCode))
        val webSafeCode = code.encodeB64().applyUrlSafetyFromB64()
        val url = SyncBarcodeUrl(webSafeCode).asUrl()
        val type = syncRepo.parseSyncAuthCode(url)
        assertTrue(type is SyncAuthCode.Exchange)
    }

    @Test
    fun whenCodeIsUrlWithRecoveryInsideThenCodeTypeIsNotIdentified() {
        val code = recoveryCodeAdapter.toJson((LinkCode(recovery = RecoveryCode(deviceId, secretKey))))
        val webSafeCode = code.encodeB64().applyUrlSafetyFromB64()
        val url = SyncBarcodeUrl(webSafeCode).asUrl()
        val type = syncRepo.parseSyncAuthCode(url)
        assertTrue(type is SyncAuthCode.Unknown)
    }

    @Test
    fun whenSignedInAndProcessRecoveryCodeIfAllowSwitchAccountTrueThenSwitchAccountIfOnly1DeviceConnected() {
        givenAuthenticatedDevice()
        givenAccountWithConnectedDevices(1)
        doAnswer {
            givenUnauthenticatedDevice() // simulate logout locally
            logoutSuccess
        }.`when`(syncApi).logout(token, deviceId)
        prepareForLoginSuccess()

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded))

        verify(syncApi).logout(token, deviceId)
        verify(syncApi).login(userId, hashedPassword, deviceId, deviceName, deviceFactor)

        assertTrue(result is Success)
    }

    @Test
    fun whenSignedInAndProcessRecoveryCodeIfAllowSwitchAccountTrueThenReturnErrorIfMultipleDevicesConnected() {
        givenAuthenticatedDevice()
        givenAccountWithConnectedDevices(2)
        doAnswer {
            givenUnauthenticatedDevice() // simulate logout locally
            logoutSuccess
        }.`when`(syncApi).logout(token, deviceId)
        prepareForLoginSuccess()

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded))

        assertEquals((result as Error).code, ALREADY_SIGNED_IN.code)
    }

    @Test
    fun whenLogoutAndJoinNewAccountSucceedsThenReturnSuccess() {
        givenAuthenticatedDevice()
        doAnswer {
            givenUnauthenticatedDevice() // simulate logout locally
            logoutSuccess
        }.`when`(syncApi).logout(token, deviceId)
        prepareForLoginSuccess()

        val result = syncRepo.logoutAndJoinNewAccount(jsonRecoveryKeyEncoded)

        assertTrue(result is Success)
        verify(syncStore).clearAll()
        verify(syncStore).storeCredentials(
            userId = userId,
            deviceId = deviceId,
            deviceName = deviceName,
            primaryKey = primaryKey,
            secretKey = secretKey,
            token = token,
        )
    }

    @Test
    fun whenGenerateKeysFromRecoveryCodeFailsThenReturnLoginFailedError() {
        prepareToProvideDeviceIds()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(failedLoginKeys)

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded)) as Error

        assertEquals(LOGIN_FAILED.code, result.code)
    }

    @Test
    fun whenLoginFailsThenReturnLoginFailedError() {
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginFailed)

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded)) as Error

        assertEquals(LOGIN_FAILED.code, result.code)
    }

    @Test
    fun whenProcessRecoveryKeyAndDecryptSecretKeyFailsThenReturnLoginFailedError() {
        prepareToProvideDeviceIds()
        prepareForEncryption()
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.decrypt(encryptedData = protectedEncryptionKey, secretKey = stretchedPrimaryKey)).thenReturn(invalidDecryptedSecretKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginSuccess)

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonRecoveryKeyEncoded)) as Error

        assertEquals(LOGIN_FAILED.code, result.code)
    }

    @Test
    fun whenProcessInvalidCodeThenReturnInvalidCodeError() {
        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode("invalidCode")) as Error

        assertEquals(INVALID_CODE.code, result.code)
    }

    @Test
    fun getConnectedDevicesSucceedsThenReturnSuccess() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        val result = syncRepo.getConnectedDevices() as Success

        assertEquals(listOfConnectedDevices, result.data)
    }

    @Test
    fun getConnectedDevicesSucceedsThenNotifyDevicesObserver() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        prepareForEncryption()
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesSuccess)

        val result = syncRepo.getConnectedDevices() as Success

        verify(connectedDevicesObserver).onDevicesUpdated(any())
    }

    @Test
    fun getConnectedDevicesReturnsListWithLocalDeviceInFirstPosition() {
        givenAuthenticatedDevice()
        prepareForEncryption()
        val thisDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherDevice = Device(deviceId = "anotherDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        val anotherRemoteDevice = Device(deviceId = "anotherRemoteDeviceId", deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(listOf(anotherDevice, anotherRemoteDevice, thisDevice)))

        val result = syncRepo.getConnectedDevices() as Success

        assertTrue(result.data.first().thisDevice)
    }

    @Test
    fun getConnectedDevicesFailsThenReturnGenericError() {
        whenever(syncStore.token).thenReturn(token)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncApi.getDevices(anyString())).thenReturn(getDevicesError)

        val result = syncRepo.getConnectedDevices() as Error

        assertEquals(GENERIC_ERROR.code, result.code)
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
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(listOf(thisDevice, otherDevice)))
        whenever(syncApi.logout("token", "otherDeviceId")).thenReturn(Success(Logout("otherDeviceId")))

        val result = syncRepo.getConnectedDevices() as Success
        verify(syncApi).logout("token", "otherDeviceId")
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun whenGenerateRecoveryCodeAsStringThenReturnExpectedJson() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)

        val result = syncRepo.getRecoveryCode() as Success

        assertEquals(jsonRecoveryKeyEncoded, result.data.rawCode)
    }

    @Test
    fun whenGenerateRecoveryCodeWithoutAccountThenReturnGenericError() {
        val result = syncRepo.getRecoveryCode() as Error

        assertEquals(GENERIC_ERROR.code, result.code)
    }

    @Test
    fun whenGetConnectQRThenReturnExpectedJson() {
        whenever(nativeLib.prepareForConnect()).thenReturn(connectKeys)
        prepareToProvideDeviceIds()

        val result = syncRepo.getConnectQR() as Success

        assertEquals(jsonConnectKeyEncoded, result.data.rawCode)
    }

    @Test
    fun whenProcessConnectCodeFromAuthenticatedDeviceThenConnectsDevice() {
        givenAuthenticatedDevice()
        whenever(nativeLib.seal(jsonRecoveryKey, primaryKey)).thenReturn(encryptedRecoveryCode)
        whenever(syncApi.connect(token, deviceId, encryptedRecoveryCode)).thenReturn(Success(true))

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonConnectKeyEncoded))

        verify(syncApi).connect(token, deviceId, encryptedRecoveryCode)
        assertTrue(result is Success)
    }

    @Test
    fun whenProcessConnectCodeFromUnauthenticatedDeviceThenAccountCreatedAndConnects() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.isSignedIn()).thenReturn(false).thenReturn(true)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.token).thenReturn(token)
        prepareToProvideDeviceIds()
        prepareForCreateAccountSuccess()
        whenever(nativeLib.seal(jsonRecoveryKey, primaryKey)).thenReturn(encryptedRecoveryCode)
        whenever(syncApi.connect(token, deviceId, encryptedRecoveryCode)).thenReturn(Success(true))

        val result = syncRepo.processCode(syncRepo.parseSyncAuthCode(jsonConnectKeyEncoded))

        verify(syncApi).connect(token, deviceId, encryptedRecoveryCode)
        assertTrue(result is Success)
    }

    @Test
    fun whenPollingConnectionKeysAndKeysFoundThenPerformLogin() {
        prepareForLoginSuccess()
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceSuccess)
        whenever(nativeLib.sealOpen(encryptedRecoveryCode, primaryKey, secretKey)).thenReturn(jsonRecoveryKey)

        val result = syncRepo.pollConnectionKeys()

        assertTrue(result is Success)
    }

    @Test
    fun whenPollingConnectionAndLoginFailsThenReturnLoginError() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceSuccess)
        whenever(nativeLib.sealOpen(encryptedRecoveryCode, primaryKey, secretKey)).thenReturn(jsonRecoveryKey)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginFailed)

        val result = syncRepo.pollConnectionKeys() as Error

        assertEquals(LOGIN_FAILED.code, result.code)
    }

    @Test
    fun whenPollingConnectionAndKeysNotFoundThenReturnSuccessFalse() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceKeysNotFoundError)

        val result = syncRepo.pollConnectionKeys() as Success

        assertFalse(result.data)
    }

    @Test
    fun whenPollingConnectionAndSealOpenFailsThenReturnConnectError() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceSuccess)
        whenever(nativeLib.sealOpen(encryptedRecoveryCode, primaryKey, secretKey)).thenThrow(RuntimeException())

        val result = syncRepo.pollConnectionKeys() as Error

        assertEquals(CONNECT_FAILED.code, result.code)
    }

    @Test
    fun whenPollingConnectionAndKeysExpiredThenReturnConnectFailedError() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceKeysGoneError)

        val result = syncRepo.pollConnectionKeys() as Error

        assertEquals(CONNECT_FAILED.code, result.code)
    }

    @Test
    fun whenPollingConnectionAndKeysReceivesMalformedJsonThenReturnConnectFailedError() {
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncStore.userId).thenReturn(userId)
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.secretKey).thenReturn(secretKey)
        whenever(syncApi.connectDevice(deviceId)).thenReturn(connectDeviceSuccess)
        whenever(nativeLib.sealOpen(connectDeviceSuccess.data, primaryKey, secretKey)).thenReturn("{ malformed json")

        val result = syncRepo.pollConnectionKeys() as Error

        assertEquals(CONNECT_FAILED.code, result.code)
    }

    @Test
    fun whenGetThisConnectedDeviceThenReturnExpectedDevice() {
        givenAuthenticatedDevice()
        whenever(syncStore.deviceId).thenReturn(deviceId)
        whenever(syncStore.deviceName).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)

        val result = syncRepo.getThisConnectedDevice()!!

        assertEquals(deviceId, result?.deviceId)
        assertEquals(deviceName, result?.deviceName)
        assertEquals(deviceType, result?.deviceType)
    }

    @Test
    fun whenGetThisConnectedDeviceAndNotAuthenticatedThenReturnNull() {
        val result = syncRepo.getThisConnectedDevice()

        assertNull(result)
    }

    @Test
    fun whenRenameDeviceUnAuthenticatedThenReturnError() {
        val result = syncRepo.renameDevice(connectedDevice)

        assertTrue(result is Error)
    }

    @Test
    fun whenRenameDeviceSuccessThenReturnSuccess() {
        givenAuthenticatedDevice()
        prepareForLoginSuccess()

        val result = syncRepo.renameDevice(connectedDevice)

        verify(syncApi).login(anyString(), anyString(), eq(connectedDevice.deviceId), anyString(), anyString())
        assertTrue(result is Success)
    }

    @Test
    fun whenEncryptionNotSupportedThenSyncNotSupported() {
        whenever(syncStore.isEncryptionSupported()).thenReturn(false)

        val result = syncRepo.isSyncSupported()

        assertFalse(result)
    }

    @Test
    fun whenConnectCodeRetrievedItRespectsUrlBasedFeatureFlag() {
        whenever(nativeLib.prepareForConnect()).thenReturn(connectKeys)
        prepareToProvideDeviceIds()
        val decoratedCode = "decorated"
        whenever(syncCodeUrlWrapper.wrapCodeInUrl(any())).thenReturn(decoratedCode)

        configureUrlWrappedCodeFeatureFlagState(enabled = true).also {
            val result = syncRepo.getConnectQR() as Success
            assertEquals(jsonConnectKeyEncoded, result.data.rawCode)
            assertEquals(decoratedCode, result.data.qrCode)
        }

        configureUrlWrappedCodeFeatureFlagState(enabled = false).also {
            val result = syncRepo.getConnectQR() as Success
            assertEquals(jsonConnectKeyEncoded, result.data.rawCode)
            assertEquals(jsonConnectKeyEncoded, result.data.qrCode)
        }
    }

    @Test
    fun whenExchangeCodeRetrievedItRespectsUrlBasedFeatureFlag() {
        prepareForExchangeSuccess()
        whenever(deviceKeyGenerator.generate()).thenReturn(primaryDeviceKeyId)

        val encodedJsonExchange = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey).encodeB64()
        val decoratedCode = "decorated"
        whenever(syncCodeUrlWrapper.wrapCodeInUrl(any())).thenReturn(decoratedCode)

        configureUrlWrappedCodeFeatureFlagState(enabled = true).also {
            val result = syncRepo.generateExchangeInvitationCode() as Success
            assertEquals(encodedJsonExchange, result.data.rawCode)
            assertEquals(decoratedCode, result.data.qrCode)
        }

        configureUrlWrappedCodeFeatureFlagState(enabled = false).also {
            val result = syncRepo.generateExchangeInvitationCode() as Success
            assertEquals(encodedJsonExchange, result.data.rawCode)
            assertEquals(encodedJsonExchange, result.data.qrCode)
        }
    }

    @Test
    fun whenRecoveryCodeRetrievedItRespectsUrlBasedFeatureFlag() {
        whenever(syncStore.primaryKey).thenReturn(primaryKey)
        whenever(syncStore.userId).thenReturn(userId)

        val decoratedCode = "decorated"
        whenever(syncCodeUrlWrapper.wrapCodeInUrl(any())).thenReturn(decoratedCode)

        // even feature is enabled, recovery codes don't have their QR codes decorated
        configureUrlWrappedCodeFeatureFlagState(enabled = true).also {
            val result = syncRepo.getRecoveryCode() as Success
            assertEquals(jsonRecoveryKeyEncoded, result.data.rawCode)
            assertEquals(jsonRecoveryKeyEncoded, result.data.qrCode)
        }

        configureUrlWrappedCodeFeatureFlagState(enabled = false).also {
            val result = syncRepo.getRecoveryCode() as Success
            assertEquals(jsonRecoveryKeyEncoded, result.data.rawCode)
            assertEquals(jsonRecoveryKeyEncoded, result.data.qrCode)
        }
    }

    @Test
    fun whenAccountDeletedWithASingleConnectedDeviceThenPixelFired() {
        prepareForExchangeSuccess()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)
        configureAsSignedWithConnectedDevices(1)
        syncRepo.deleteAccount()
        verify(syncPixels).fireUserConfirmedToTurnOffSyncAndDelete(eq(1))
    }

    @Test
    fun whenAccountDeletedWithMultipleConnectedDevicesThenPixelFired() {
        prepareForExchangeSuccess()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)
        configureAsSignedWithConnectedDevices(10)
        syncRepo.deleteAccount()
        verify(syncPixels).fireUserConfirmedToTurnOffSyncAndDelete(eq(10))
    }

    @Test
    fun whenAccountDeletedWithNoConnectedDevicesThenPixelFired() {
        prepareForExchangeSuccess()
        whenever(syncApi.deleteAccount(token)).thenReturn(deleteAccountSuccess)
        syncRepo.deleteAccount()
        verify(syncPixels).fireUserConfirmedToTurnOffSyncAndDelete(eq(0))
    }

    private fun configureUrlWrappedCodeFeatureFlagState(enabled: Boolean) {
        syncFeature.syncSetupBarcodeIsUrlBased().setRawStoredState(State(enable = enabled))
    }

    private fun prepareForLoginSuccess() {
        prepareForEncryption()
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(syncApi.login(userId, hashedPassword, deviceId, deviceName, deviceFactor)).thenReturn(loginSuccess)
    }

    private fun prepareForExchangeSuccess() {
        prepareForEncryption()
        syncFeature.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(true))
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)
        whenever(syncApi.sendEncryptedMessage(eq(primaryDeviceKeyId), eq(encryptedExchangeCode))).thenReturn(Success(true))
        whenever(nativeLib.prepareForLogin(primaryKey = primaryKey)).thenReturn(validLoginKeys)
        whenever(nativeLib.prepareForConnect()).thenReturn(connectKeys)
        whenever(nativeLib.seal(any(), eq(primaryKey))).thenReturn(encryptedExchangeCode)
    }

    private fun initiateInvitationAsPrimaryDevice() {
        whenever(deviceKeyGenerator.generate()).thenReturn(primaryDeviceKeyId)
        syncRepo.generateExchangeInvitationCode()
        val otherDeviceDetails = InvitedDeviceDetails(keyId = otherDeviceKeyId, publicKey = "otherDevicePublicKey", deviceName = "otherDeviceName")
        val json = invitedDeviceDetailsAdapter.toJson(otherDeviceDetails)
        whenever(nativeLib.sealOpen(any(), eq(primaryKey), eq(secretKey))).thenReturn(json)
        whenever(nativeLib.seal(any(), eq("otherDevicePublicKey"))).thenReturn(encryptedExchangeCode)
        whenever(syncApi.getEncryptedMessage(primaryDeviceKeyId)).thenReturn(Success(json))
        whenever(syncApi.sendEncryptedMessage(eq(otherDeviceKeyId), eq(encryptedExchangeCode))).thenReturn(Success(true))
    }

    private fun parseInvitationCodeJson(resultJson: Result<AuthCode>): InvitationCodeWrapper {
        assertTrue(resultJson is Success)
        return invitationCodeWrapperAdapter.fromJson(resultJson.getOrNull()?.rawCode!!.decodeB64())!!
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

    private fun givenUnauthenticatedDevice() {
        whenever(syncStore.isSignedIn()).thenReturn(false)
    }

    private fun prepareToProvideDeviceIds() {
        whenever(syncDeviceIds.userId()).thenReturn(userId)
        whenever(syncDeviceIds.deviceId()).thenReturn(deviceId)
        whenever(syncDeviceIds.deviceName()).thenReturn(deviceName)
        whenever(syncDeviceIds.deviceType()).thenReturn(deviceType)
    }

    private fun prepareForCreateAccountSuccess() {
        prepareForEncryption()
        whenever(nativeLib.generateAccountKeys(userId = anyString(), password = anyString())).thenReturn(accountKeys)
        whenever(syncApi.createAccount(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Success(AccountCreatedResponse(userId, token)))
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

    private fun givenAccountWithConnectedDevices(size: Int) {
        prepareForEncryption()
        val listOfDevices = mutableListOf<Device>()
        for (i in 0 until size) {
            listOfDevices.add(aDevice.copy(deviceId = "device$i"))
        }
        whenever(syncApi.getDevices(anyString())).thenReturn(Success(listOfDevices))

        syncRepo.getConnectedDevices() as Success
    }
}
