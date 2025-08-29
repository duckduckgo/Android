/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.deviceauth

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator.AuthConfiguration

abstract class FakeAuthenticator : DeviceAuthenticator {

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data object Failure : Result
    }

    var authenticateCalled: Boolean = false
    abstract val authResult: Result

    private fun authenticationCalled(onResult: (DeviceAuthenticator.AuthResult) -> Unit) {
        authenticateCalled = true
        when (authResult) {
            is Result.Success -> onResult(DeviceAuthenticator.AuthResult.Success)
            is Result.Cancelled -> onResult(DeviceAuthenticator.AuthResult.UserCancelled)
            is Result.Failure -> onResult(DeviceAuthenticator.AuthResult.Error("Authentication failed"))
        }
    }

    override fun hasValidDeviceAuthentication(): Boolean = true

    override fun authenticate(
        fragment: Fragment,
        config: AuthConfiguration,
        onResult: (DeviceAuthenticator.AuthResult) -> Unit,
    ) {
        authenticationCalled(onResult)
    }

    override fun authenticate(
        fragmentActivity: FragmentActivity,
        config: AuthConfiguration,
        onResult: (DeviceAuthenticator.AuthResult) -> Unit,
    ) {
        authenticationCalled(onResult)
    }

    class AuthorizeEverything(override val authResult: Result = Result.Success) : FakeAuthenticator()
    class FailEverything(override val authResult: Result = Result.Failure) : FakeAuthenticator()
    class CancelEverything(override val authResult: Result = Result.Cancelled) : FakeAuthenticator()
}
