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

package com.duckduckgo.sync

import android.graphics.Bitmap
import com.duckduckgo.sync.crypto.AccountKeys
import com.duckduckgo.sync.crypto.ConnectKeys
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.LoginKeys
import com.duckduckgo.sync.impl.AccountCreatedResponse
import com.duckduckgo.sync.impl.Connect
import com.duckduckgo.sync.impl.ConnectKey
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Device
import com.duckduckgo.sync.impl.DeviceEntries
import com.duckduckgo.sync.impl.DeviceResponse
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.Login
import com.duckduckgo.sync.impl.LoginResponse
import com.duckduckgo.sync.impl.Logout
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Signup
import com.duckduckgo.sync.impl.encodeB64
import com.duckduckgo.sync.impl.parser.SyncBookmarkEntry
import com.duckduckgo.sync.impl.parser.SyncBookmarkUpdates
import com.duckduckgo.sync.impl.parser.SyncDataRequest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

object TestSyncFixtures {
    const val userId = "userId"
    const val password = "password"
    const val deviceId = "deviceId"
    const val deviceName = "deviceName"
    const val deviceFactor = "phone"
    val deviceType = DeviceType(deviceFactor)
    const val token = "token"
    const val primaryKey = "primaryKey"
    const val stretchedPrimaryKey = "primaryKey"
    const val secretKey = "secretKey"
    const val hashedPassword = "hashedPassword"
    const val protectedEncryptionKey = "protectedEncryptionKey"
    const val encryptedRecoveryCode = "encrypted_recovery_code"
    val accountKeys = AccountKeys(
        result = 0L,
        userId = userId,
        password = password,
        primaryKey = primaryKey,
        secretKey = secretKey,
        protectedSecretKey = protectedEncryptionKey,
        passwordHash = hashedPassword,
    )
    val accountKeysFailed = AccountKeys(
        result = 9L,
        userId = userId,
        password = password,
        primaryKey = "",
        secretKey = "",
        protectedSecretKey = "",
        passwordHash = "",
    )
    val accountCreated = AccountCreatedResponse(userId, token)
    val signUpRequest = Signup(userId, hashedPassword, protectedEncryptionKey, deviceId, deviceName, deviceFactor)
    val signupSuccess: Response<AccountCreatedResponse> = Response.success(accountCreated)
    const val duplicateUsercCodeErr = 409
    const val duplicateUserMessageErr = "Invalid hashed_password. Must be 32 bytes encoded in Base64URL."
    val signupFailDuplicatedUser: Response<AccountCreatedResponse> = Response.error(
        duplicateUsercCodeErr,
        "{\"code\": $duplicateUsercCodeErr,\"error\": \"$duplicateUserMessageErr\"}".toResponseBody(),
    )
    const val invalidCodeErr = 400
    const val invalidMessageErr = "Invalid hashed_password. Must be 32 bytes encoded in Base64URL."
    val signupFailInvalid: Response<AccountCreatedResponse> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )
    val accountCreatedSuccess = Result.Success(AccountCreatedResponse(userId = userId, token = token))
    val accountCreatedFailInvalid = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val accountCreatedFailDupUser = Result.Error(code = duplicateUsercCodeErr, reason = duplicateUserMessageErr)

    val deviceLogoutBody = Logout(deviceId)
    val deviceLoggedOutBody = Logout(deviceId)
    val deviceLogoutResponse = Response.success(deviceLoggedOutBody)
    const val wrongCredentialsCodeErr = 401
    const val wrongCredentialsMessageErr = "invalid_login_credentials"
    val logoutError = Response.error<Logout>(
        wrongCredentialsCodeErr,
        "{\"error\": \"$wrongCredentialsMessageErr\"}".toResponseBody(),
    )
    val logoutSuccess = Result.Success(deviceLoggedOutBody)
    val logoutInvalid = Result.Error(code = wrongCredentialsCodeErr, reason = wrongCredentialsMessageErr)

    val deleteAccountResponse = Response.success<Void>(null)
    val deleteAccountError = Response.error<Void>(
        wrongCredentialsCodeErr,
        "{\"error\": \"$wrongCredentialsMessageErr\"}".toResponseBody(),
    )
    val deleteAccountSuccess = Result.Success(true)
    val deleteAccountInvalid = Result.Error(code = wrongCredentialsCodeErr, reason = wrongCredentialsMessageErr)

    val jsonRecoveryKey = "{\"recovery\":{\"primary_key\":\"$primaryKey\",\"user_id\":\"$userId\"}}"
    val jsonConnectKey = "{\"connect\":{\"device_id\":\"$deviceId\",\"secret_key\":\"$primaryKey\"}}"
    val jsonRecoveryKeyEncoded = jsonRecoveryKey.encodeB64()
    val jsonConnectKeyEncoded = jsonConnectKey.encodeB64()
    val connectKeys = ConnectKeys(0L, publicKey = primaryKey, secretKey = secretKey)
    val validLoginKeys = LoginKeys(result = 0L, passwordHash = hashedPassword, stretchedPrimaryKey = stretchedPrimaryKey, primaryKey = primaryKey)
    val failedLoginKeys = LoginKeys(result = 9L, passwordHash = "", stretchedPrimaryKey = "", primaryKey = "")
    val decryptedSecretKey = DecryptResult(result = 0L, decryptedData = secretKey)
    val invalidDecryptedSecretKey = DecryptResult(result = 9L, decryptedData = "")
    val loginResponseBody = LoginResponse(
        token = token,
        protected_encryption_key = protectedEncryptionKey,
        devices = emptyList(),
    )
    val loginSuccess = Result.Success(loginResponseBody)
    val loginError = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val loginFailedInvalidResponse: Response<LoginResponse> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )
    val loginFailed = Result.Error(code = wrongCredentialsCodeErr, reason = wrongCredentialsMessageErr)
    val loginRequestBody = Login(
        userId = userId,
        hashedPassword = hashedPassword,
        deviceId = deviceId,
        deviceName = deviceName,
        deviceType = deviceFactor,
    )
    val loginSuccessResponse: Response<LoginResponse> = Response.success(loginResponseBody)

    val listOfDevices = listOf(Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor))
    val deviceResponse = DeviceResponse(DeviceEntries(listOfDevices))
    val getDevicesBodySuccessResponse: Response<DeviceResponse> = Response.success(deviceResponse)
    val getDevicesBodyErrorResponse: Response<DeviceResponse> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )

    val getDevicesSuccess = Result.Success(listOfDevices)
    val getDevicesError = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val listOfConnectedDevices = listOf(ConnectedDevice(thisDevice = true, deviceName = deviceName, deviceId = deviceId, deviceType = deviceType))

    val connectKey = ConnectKey(encryptedRecoveryCode)
    const val keysNotFoundErr = "connection_keys_not_found"
    const val keysNotFoundCode = 404
    val connectSuccess = Result.Success(true)
    val connectError = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val connectResponse = Response.success<Void>(null)
    val connectInvalid: Response<Void> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )
    val connectBody = Connect(deviceId = deviceId, encryptedRecoveryKey = encryptedRecoveryCode)

    val connectDeviceBody = ConnectKey(
        encryptedRecoveryKey = encryptedRecoveryCode,
    )
    val connectDeviceResponse = Response.success<ConnectKey>(connectDeviceBody)
    val connectDeviceErrorResponse: Response<ConnectKey> = Response.error(
        keysNotFoundCode,
        "{\"error\":\"$keysNotFoundErr\"}".toResponseBody(),
    )
    val connectDeviceSuccess = Result.Success(encryptedRecoveryCode)
    val connectDeviceKeysNotFoundError = Result.Error(code = keysNotFoundCode, reason = keysNotFoundErr)
    val patchAllSuccess = Result.Success(true)
    val patchAllError = Result.Error(-1, "Patch All Error")

    private fun aBookmarkEntry(index: Int): SyncBookmarkEntry {
        return SyncBookmarkEntry.asBookmark("bookmark$index", "title$index", "https://bookmark$index.com", null)
    }

    private fun aBookmarkFolderEntry(
        index: Int,
        children: List<Int>,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry.asFolder("folder$index", "title$index", children.map { "bookmark$index" }, null)
    }

    private fun someBookmarkEntries(): SyncBookmarkUpdates {
        return SyncBookmarkUpdates(
            listOf(
                aBookmarkEntry(1),
                aBookmarkEntry(2),
                aBookmarkEntry(3),
                aBookmarkFolderEntry(1, listOf(1, 2, 3)),
            ),
        )
    }

    val syncData = SyncDataRequest(someBookmarkEntries())

    fun qrBitmap(): Bitmap {
        return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    }
}
