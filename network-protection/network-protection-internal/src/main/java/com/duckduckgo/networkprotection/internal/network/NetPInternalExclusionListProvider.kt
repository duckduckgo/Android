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

package com.duckduckgo.networkprotection.internal.network

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.networkprotection.impl.NetPDebugExclusionListProvider
import com.duckduckgo.networkprotection.internal.feature.NetPFeatureConfig
import com.duckduckgo.networkprotection.internal.feature.NetPSetting
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class NetPInternalExclusionListProvider @Inject constructor(
    private val packageManager: PackageManager,
    private val netPFeatureConfig: NetPFeatureConfig,
) : NetPDebugExclusionListProvider {
    override fun getExclusionList(): Set<String> {
        if (!netPFeatureConfig.isEnabled(NetPSetting.ExcludeSystemApps)) return emptySet()

        // returns the list of system apps for now
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .map { it.packageName }
            .toSet()
    }
}
