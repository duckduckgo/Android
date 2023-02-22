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

package com.duckduckgo.networkprotection.internal.feature.system_apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.internal.network.NetPInternalExclusionListProvider
import dagger.WrongScope
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import logcat.logcat
import om.duckduckgo.networkprotection.internal.databinding.ActivityNetpInternalSystemAppsExclusionBinding

@WrongScope(
    comment = "VpnScope to access dependencies in there",
    correctScope = ActivityScope::class,
)
@InjectWith(VpnScope::class)
class NetPSystemAppsExclusionListActivity : DuckDuckGoActivity(), SystemAppView.SystemAppListener {

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var exclusionListProvider: NetPInternalExclusionListProvider

    @Inject
    lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    private var initialExclusionList: Int? = null

    private val binding: ActivityNetpInternalSystemAppsExclusionBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.progress.isVisible = true

        packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .sortedBy { it.packageName }
            .map { it.packageName }
            .asFlow()
            .flowOn(dispatchers.io())
            .map { packageName ->
                SystemAppView(this).apply {
                    systemAppPackageName = packageName
                    systemAppClickListener = this@NetPSystemAppsExclusionListActivity
                    isChecked = !exclusionListProvider.getExclusionList().firstOrNull { it == packageName }.isNullOrBlank()
                    tag = packageName
                }
            }
            .onEach { systemAppView ->
                binding.systemApps.addView(systemAppView)
                binding.systemApps.isVisible = true
                binding.progress.isVisible = false
            }
            .flowOn(dispatchers.main())
            .launchIn(lifecycleScope)
    }

    override fun onResume() {
        super.onResume()
        initialExclusionList = exclusionListProvider.getExclusionList().hashCode()
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch {
            if (initialExclusionList != exclusionListProvider.getExclusionList().hashCode()) {
                vpnFeaturesRegistry.refreshFeature(NetPVpnFeature.NETP_VPN)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, NetPSystemAppsExclusionListActivity::class.java)
        }
    }

    override fun onViewClicked(view: View, enabled: Boolean) {
        val packageName = (view.tag as String?) ?: return
        logcat { "System app $packageName clicked" }
        if (enabled) {
            exclusionListProvider.excludeSystemApp(packageName)
        } else {
            exclusionListProvider.includeSystemApp(packageName)
        }
    }
}
