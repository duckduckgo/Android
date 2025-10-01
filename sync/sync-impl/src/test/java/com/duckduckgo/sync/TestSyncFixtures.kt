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
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.File

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
    const val encryptedExchangeCode = "encrypted_exchange_code"
    const val primaryDeviceKeyId = "primary_device_key_id"
    const val otherDeviceKeyId = "other_device_key_id"
    val accountKeys = AccountKeys(
        result = 0,
        userId = userId,
        password = password,
        primaryKey = primaryKey,
        secretKey = secretKey,
        protectedSecretKey = protectedEncryptionKey,
        passwordHash = hashedPassword,
    )
    val accountKeysFailed = AccountKeys(
        result = 9,
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
    val connectKeys = ConnectKeys(0, publicKey = primaryKey, secretKey = secretKey)
    val validLoginKeys = LoginKeys(result = 0, passwordHash = hashedPassword, stretchedPrimaryKey = stretchedPrimaryKey, primaryKey = primaryKey)
    val failedLoginKeys = LoginKeys(result = 9, passwordHash = "", stretchedPrimaryKey = "", primaryKey = "")
    val decryptedSecretKey = DecryptResult(result = 0, decryptedData = secretKey)
    val invalidDecryptedSecretKey = DecryptResult(result = 9, decryptedData = "")
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

    val aDevice = Device(deviceId = deviceId, deviceName = deviceName, jwIat = "", deviceType = deviceFactor)
    val listOfDevices = listOf(aDevice)
    val deviceResponse = DeviceResponse(DeviceEntries(listOfDevices))
    val getDevicesBodySuccessResponse: Response<DeviceResponse> = Response.success(deviceResponse)
    val getDevicesBodyErrorResponse: Response<DeviceResponse> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )
    val getDevicesBodyInvalidCodeResponse: Response<DeviceResponse> = Response.error(
        wrongCredentialsCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )

    val getDevicesSuccess = Result.Success(listOfDevices)
    val getDevicesError = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val invalidCredentialsError = Result.Error(code = wrongCredentialsCodeErr, reason = invalidMessageErr)
    val connectedDevice = ConnectedDevice(thisDevice = true, deviceName = deviceName, deviceId = deviceId, deviceType = deviceType)
    val listOfConnectedDevices = listOf(connectedDevice)

    val connectKey = ConnectKey(encryptedRecoveryCode)
    const val keysNotFoundErr = "connection_keys_not_found"
    const val keysNotFoundCode = 404
    const val keysGoneCode = 410
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
    val connectDeviceKeysGoneError = Result.Error(code = keysGoneCode, reason = keysNotFoundErr)

    val firstSyncWithBookmarksAndFavorites = "{\"bookmarks\":{\"updates\":[{\"client_last_modified\":\"timestamp\"" +
        ",\"folder\":{\"children\":[\"bookmark1\"]},\"id\":\"favorites_root\",\"title\":\"Favorites\"},{\"client_last_modified\"" +
        ":\"timestamp\",\"id\":\"bookmark3\",\"page\":{\"url\":\"https://bookmark3.com\"},\"title\":\"Bookmark 3\"}" +
        ",{\"client_last_modified\":\"timestamp\",\"id\":\"bookmark4\",\"page\":{\"url\":\"https://bookmark4.com\"}" +
        ",\"title\":\"Bookmark 4\"},{\"client_last_modified\":\"timestamp\",\"folder\":{\"children\":[\"bookmark3\"," +
        "\"bookmark4\"]},\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"}]}}"

    fun qrBitmap(): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    fun pdfFile(): File = File("Sync Data Recovery - DuckDuckGo.pdf")

    fun jsonExchangeKey(keyId: String, publicKey: String) = """
        {"exchange_key":{"key_id":"$keyId","public_key":"$publicKey"}}
    """.trimIndent()
}
