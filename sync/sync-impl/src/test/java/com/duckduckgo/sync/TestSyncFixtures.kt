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

import com.duckduckgo.sync.crypto.AccountKeys
import com.duckduckgo.sync.crypto.DecryptResult
import com.duckduckgo.sync.crypto.LoginKeys
import com.duckduckgo.sync.impl.AccountCreatedResponse
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.Device
import com.duckduckgo.sync.impl.DeviceEntries
import com.duckduckgo.sync.impl.DeviceResponse
import com.duckduckgo.sync.impl.Login
import com.duckduckgo.sync.impl.LoginResponse
import com.duckduckgo.sync.impl.Logout
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Signup
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

object TestSyncFixtures {
    const val userId = "userId"
    const val password = "password"
    const val deviceId = "deviceId"
    const val deviceName = "deviceName"
    const val token = "token"
    const val primaryKey = "primaryKey"
    const val stretchedPrimaryKey = "primaryKey"
    const val secretKey = "secretKey"
    const val hashedPassword = "hashedPassword"
    const val protectedEncryptionKey = "protectedEncryptionKey"
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
    val signUpRequest = Signup(userId, hashedPassword, protectedEncryptionKey, deviceId, deviceName)
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
    val accountCreatedSuccess = Result.Success(AccountCreatedResponse(user_id = userId, token = token))
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

    val jsonRecoveryKey = "{\"primaryKey\": \"$primaryKey\",\"userID\": \"$userId\"}"
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
    val loginRequestBody = Login(user_id = userId, hashed_password = hashedPassword, device_id = deviceId, device_name = deviceName)
    val loginSuccessResponse: Response<LoginResponse> = Response.success(loginResponseBody)

    val listOfDevices = listOf(Device(device_id = deviceId, device_name = deviceName, jw_iat = ""))
    val deviceResponse = DeviceResponse(DeviceEntries(listOfDevices))
    val getDevicesBodySuccessResponse: Response<DeviceResponse> = Response.success(deviceResponse)
    val getDevicesBodyErrorResponse: Response<DeviceResponse> = Response.error(
        invalidCodeErr,
        "{\"error\":\"$invalidMessageErr\"}".toResponseBody(),
    )

    val getDevicesSuccess = Result.Success(listOfDevices)
    val getDevicesError = Result.Error(code = invalidCodeErr, reason = invalidMessageErr)
    val listOfConnectedDevices = listOf(ConnectedDevice(thisDevice = true, deviceName = deviceName, deviceId = deviceId))
}
