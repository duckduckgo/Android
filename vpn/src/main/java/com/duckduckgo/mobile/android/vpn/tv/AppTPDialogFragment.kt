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

package com.duckduckgo.mobile.android.vpn.tv

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldEnabledActivity

class AppTPDialogFragment : GuidedStepSupportFragment() {

    private val ACTION_ID_POSITIVE = 1
    private val ACTION_ID_NEGATIVE = ACTION_ID_POSITIVE + 1

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ASK_VPN_PERMISSION) {
            when (resultCode) {
                AppCompatActivity.RESULT_OK -> {
                    startVpn()
                    return
                }
                else -> {
                    Toast.makeText(activity, "Permission not granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        return Guidance(
            getString(R.string.atp_TV_DialogTitle),
            getString(R.string.atp_OnboardingLastPageOneTitle),
            "",
            null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction?>, savedInstanceState: Bundle?) {
        var action =
            GuidedAction.Builder(context).id(ACTION_ID_POSITIVE.toLong()).title("Yes").build()
        actions.add(action)
        action = GuidedAction.Builder(context).id(ACTION_ID_NEGATIVE.toLong()).title("No").build()
        actions.add(action)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (ACTION_ID_POSITIVE.toLong() == action.id) {
            startVpnIfAllowed()
            startActivity(TvTrackerDetailsActivity.intent(requireActivity()))
        } else {
            Toast.makeText(
                activity, "No Clicked",
                Toast.LENGTH_SHORT
            ).show()
            activity!!.finish()
        }
    }

    private fun startVpnIfAllowed() {
        when (val permissionStatus = checkVpnPermission()) {
            is VpnPermissionStatus.Granted -> startVpn()
            is VpnPermissionStatus.Denied -> obtainVpnRequestPermission(permissionStatus.intent)
        }
    }

    private fun checkVpnPermission(): VpnPermissionStatus {
        val intent = VpnService.prepare(activity)
        return if (intent == null) {
            VpnPermissionStatus.Granted
        } else {
            VpnPermissionStatus.Denied(intent)
        }
    }

    private fun startVpn() {
        TrackerBlockingVpnService.startService(requireContext())
        startActivity(TvTrackerDetailsActivity.intent(requireContext()))
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101
    }
}
