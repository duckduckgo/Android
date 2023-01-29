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
import com.duckduckgo.sync.impl.AccountCreatedResponse
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
}
