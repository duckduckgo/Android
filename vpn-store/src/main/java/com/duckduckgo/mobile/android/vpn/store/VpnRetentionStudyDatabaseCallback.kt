/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.store

import android.content.Context
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.trackers.*
import timber.log.Timber
import java.util.*
import javax.inject.Provider

internal class VpnRetentionStudyDatabaseCallback(
    private val context: Context,
    private val vpnDatabase: Provider<VpnDatabase>,
    private val dispatcherProvider: DispatcherProvider,
) : VpnDatabaseCallback(context, vpnDatabase, dispatcherProvider) {

    override fun getBlocklistJsonResource(): Int {
        Timber.d("We are in the Retention Study, using the reduced blocklist")
        return R.raw.reduced_app_trackers_blocklist
    }

}
