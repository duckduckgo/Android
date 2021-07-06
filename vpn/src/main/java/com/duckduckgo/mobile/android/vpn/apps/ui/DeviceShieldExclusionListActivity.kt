/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.mobile.android.ui.view.show
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.DeviceShieldExcludedApps
import com.facebook.shimmer.ShimmerFrameLayout
import dagger.android.AndroidInjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeviceShieldExclusionListActivity : AppCompatActivity(R.layout.activity_device_shield_exclusion_list) {

    @Inject
    lateinit var deviceShieldExcludedApps: DeviceShieldExcludedApps

    private val listContainer by lazy { findViewById<LinearLayout>(R.id.deviceShieldExclusionAppListContainer) }
    private val shimmerLayout by lazy { findViewById<ShimmerFrameLayout>(R.id.deviceShieldExclusionAppListSkeleton) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        shimmerLayout.startShimmer()

        lifecycleScope.launch {
            setupExclusionList().forEach { appView -> listContainer.addView(appView) }
            shimmerLayout.gone()
            listContainer.show()
        }
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun PackageManager.safeGetApplicationIcon(packageName: String): Drawable? {
        return runCatching {
            getApplicationIcon(packageName)
        }.getOrNull()
    }

    private suspend fun setupExclusionList(): Sequence<DeviceShieldExcludedAppView> = withContext(Dispatchers.IO) {
        return@withContext deviceShieldExcludedApps.getExclusionAppList().filterNot { it.isDdgApp }.asSequence()
            .map {
                DeviceShieldExcludedAppView(this@DeviceShieldExclusionListActivity).apply {
                    appName = "${it.name}"
                    appType = it.type
                    appIcon = packageManager.safeGetApplicationIcon(it.packageName)
                    isShieldEnabled = it.isExcludedFromVpn
                    tag = it.packageName
                    shieldListener = object : DeviceShieldExcludedAppView.ShieldListener {
                        override fun onAppShieldChanged(view: View, enabled: Boolean) {
                            if (enabled) {
                                deviceShieldExcludedApps.addToExclusionList((view.tag as String?).orEmpty())
                            } else {
                                deviceShieldExcludedApps.removeFromExclusionList((view.tag as String?).orEmpty())
                            }
                        }
                    }
                }
            }
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldExclusionListActivity::class.java)
        }
    }
}
