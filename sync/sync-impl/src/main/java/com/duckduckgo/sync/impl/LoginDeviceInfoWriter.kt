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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Success
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Runs after a successful native (ddg) login to bring this device into the unified device list.
 * Best-effort by design: the login has already written the legacy device name/type, so a failure here must never fail the login.
 */
interface LoginDeviceInfoWriter {
    /**
     * Do the best-effort unified-device-list write for a freshly-completed ddg login. [loginResponseKeys] is the login response's keys[],
     * used to spot a 3party-only account_info key that this native device should re-wrap for ddg.
     */
    suspend fun onLogin(loginResponseKeys: List<ProtectedKeyEntry>?): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealLoginDeviceInfoWriter @Inject constructor(
    private val syncFeature: SyncFeature,
    private val accountInfoDdgWrapRepairer: AccountInfoDdgWrapRepairer,
    private val deviceInfoMigrator: DeviceInfoMigrator,
    private val dispatchers: DispatcherProvider,
) : LoginDeviceInfoWriter {

    override suspend fun onLogin(loginResponseKeys: List<ProtectedKeyEntry>?): Result<Unit> = withContext(dispatchers.io()) {
        if (!syncFeature.canWriteDeviceInfo()) return@withContext Success(Unit)
        val accountInfoKeys = loginResponseKeys.orEmpty().filter { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO }
        if (accountInfoKeys.isEmpty()) {
            logcat { "Sync-UnifiedDevices: no account_info key in login response; migration will create it" }
        } else {
            accountInfoDdgWrapRepairer.repair(accountInfoKeys.first().kid, accountInfoKeys)
        }
        deviceInfoMigrator.ensureMigrated()
    }
}
