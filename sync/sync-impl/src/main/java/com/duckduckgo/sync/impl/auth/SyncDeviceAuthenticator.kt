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

package com.duckduckgo.sync.impl.auth

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Event
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Request
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator.Response
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationResult
import com.duckduckgo.sync.impl.pixels.SyncPixels.AuthenticationSource
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface SyncDeviceAuthenticator {
    val currentPrompt: StateFlow<AuthPrompt?>

    suspend fun authenticate(
        source: AuthenticationSource,
        request: Request,
        onEvent: (Event) -> Unit = {},
    ): Response
}

@ContributesBinding(AppScope::class)
class RealSyncDeviceAuthenticator @Inject constructor(
    private val authenticator: DeviceAuthenticator,
    private val syncPixels: SyncPixels,
    private val syncFeature: SyncFeature,
    private val dispatchers: DispatcherProvider,
) : SyncDeviceAuthenticator {
    override val currentPrompt: StateFlow<AuthPrompt?>
        get() = authenticator.currentPrompt

    override suspend fun authenticate(
        source: AuthenticationSource,
        request: Request,
        onEvent: (Event) -> Unit,
    ): Response {
        val response = authenticator.authenticate(request, onEvent)
        withContext(dispatchers.io()) {
            if (syncFeature.canUseImprovedSyncFlow().isEnabled()) {
                response.toPixelResult()?.let { result ->
                    syncPixels.fireSetupAuthenticationResult(result, source)
                }
            }
        }
        return response
    }

    private fun Response.toPixelResult(): AuthenticationResult? = when (this) {
        is Response.Allowed.UserAuthenticated -> AuthenticationResult.SUCCEEDED
        is Response.Cancelled.VerificationDismissed -> AuthenticationResult.CANCELLED
        is Response.Failed -> AuthenticationResult.FAILED
        is Response.Cancelled.EnrollmentClosed -> AuthenticationResult.UNCONFIGURED
        is Response.Allowed.WithinGracePeriod, is Response.Allowed.NotRequiredForBuild -> null
    }
}
