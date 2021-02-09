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

package com.duckduckgo.mobile.android.vpn.onboarding

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject


class DeviceShieldOnboardingActivity: AppCompatActivity(R.layout.activity_device_shield_onboarding) {

    @Inject
    lateinit var deviceShieldOnboardingStore: DeviceShieldOnboardingStore

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AndroidInjection.inject(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<Button>(R.id.askVpnPermissionButton).setOnClickListener {
            when(val intent = VpnService.prepare(this)) {
                null -> startVpn()
                else -> startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
            }
        }
    }

    override fun onBackPressed() {
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        deviceShieldOnboardingStore.onboardingDidNotShow()
        finish()
        return true
    }

    override fun onStart() {
        super.onStart()
        deviceShieldOnboardingStore.onboardingDidShow()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            when(resultCode) {
                RESULT_OK -> {
                    startVpn()
                    return
                }
                else -> Timber.d("Permission not granted")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun startVpn() {
        startService(TrackerBlockingVpnService.startIntent(this))
        setResult(RESULT_OK)
        finish()
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101
    }
}