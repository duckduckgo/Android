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

package com.duckduckgo.sync.impl.sendtodevices

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.common.ui.applyBottomSystemBarInsetPadding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.listitem.OneLineListItem
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.api.SendToDevices
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.BottomSheetSendToDevicesBinding
import com.duckduckgo.sync.impl.iconRes
import com.duckduckgo.sync.store.SyncStore
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@ContributesBinding(ActivityScope::class)
class RealSendToDevices @Inject constructor(
    private val activity: AppCompatActivity,
    private val sendLinksSetting: SendLinksSetting,
    private val syncStore: SyncStore,
    private val dispatchers: DispatcherProvider,
) : SendToDevices {

    override suspend fun showShareSheet(url: String, title: String, onMoreOptions: () -> Unit): Boolean {
        val devices = withContext(dispatchers.io()) {
            if (!syncStore.isSignedIn() || !sendLinksSetting.isEnabled()) return@withContext emptyList()
            runCatching { sendLinksSetting.otherDevices() }.getOrDefault(emptyList())
        }
        if (devices.isEmpty()) return false

        val dialog = BottomSheetDialog(activity, CommonR.style.Widget_DuckDuckGo_BottomSheetDialog_EdgeToEdge)
        val binding = BottomSheetSendToDevicesBinding.inflate(LayoutInflater.from(dialog.context))
        binding.root.applyBottomSystemBarInsetPadding()
        devices.forEach { device ->
            binding.devices.addView(
                OneLineListItem(dialog.context).apply {
                    setPrimaryText(device.deviceName)
                    setLeadingIconResource(device.deviceType.iconRes())
                    setLeadingIconVisibility(true)
                    setClickListener {
                        dialog.dismiss()
                        activity.lifecycleScope.launch {
                            val sent = withContext(dispatchers.io()) { sendLinksSetting.send(device, url, title) }
                            if (!sent) {
                                Toast.makeText(activity, R.string.send_to_devices_too_long, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            Toast.makeText(activity, activity.getString(R.string.send_to_devices_sent, device.deviceName), Toast.LENGTH_SHORT).show()
                            requestNotificationPermissionIfNeeded()
                        }
                    }
                },
            )
        }
        binding.moreOptions.setClickListener {
            dialog.dismiss()
            onMoreOptions()
        }
        dialog.setContentView(binding.root)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.setOnShowListener { dialog.setRoundCorners() }
        dialog.show()
        return true
    }

    // Links can be sent back to this device, so ask once the user has used the feature.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ActivityCompat.checkSelfPermission(activity, POST_NOTIFICATIONS) == PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(activity, arrayOf(POST_NOTIFICATIONS), 0)
    }
}
