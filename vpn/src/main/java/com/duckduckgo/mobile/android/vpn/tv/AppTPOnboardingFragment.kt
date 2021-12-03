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

import android.animation.Animator
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.leanback.app.OnboardingSupportFragment
import androidx.preference.PreferenceManager
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService

class AppTPOnboardingFragment : OnboardingSupportFragment() {

    private lateinit var contentAnimator: Animator
    private lateinit var contentView: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
        logoResourceId = R.drawable.logo_full
    }

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


    override fun onFinishFragment() {
        super.onFinishFragment()

        PreferenceManager.getDefaultSharedPreferences(activity).edit().apply {
            putBoolean(TvLauncherActivity.COMPLETED_ONBOARDING_PREF_KEY, true)
            apply()
        }

        startVpnIfAllowed()
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
        activity!!.finish()
    }

    @Suppress("DEPRECATION")
    private fun obtainVpnRequestPermission(intent: Intent) {
        startActivityForResult(intent, REQUEST_ASK_VPN_PERMISSION)
    }

    private sealed class VpnPermissionStatus {
        object Granted : VpnPermissionStatus()
        data class Denied(val intent: Intent) : VpnPermissionStatus()
    }

    override fun getPageCount(): Int {
        return pages.size
    }

    override fun getPageTitle(pageIndex: Int): CharSequence {
        return getString(pages[pageIndex].title)
    }

    override fun getPageDescription(pageIndex: Int): CharSequence {
        return getString(pages[pageIndex].text)
    }

    override fun onCreateBackgroundView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        return null
    }

    override fun onCreateContentView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        contentView = ImageView(activity)
        contentView.setScaleType(ImageView.ScaleType.CENTER_INSIDE)
        contentView.setPadding(0, 32, 0, 32)
        contentView.setImageResource(R.drawable.logo_full)
        return contentView
    }

    override fun onCreateForegroundView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        return null
    }

    companion object {
        private const val REQUEST_ASK_VPN_PERMISSION = 101
        data class OnboardingPage(val imageHeader: Int, val title: Int, val text: Int)
        val pages =
            listOf(
                OnboardingPage(
                    R.drawable.logo_full,
                    R.string.atp_OnboardingLastPageOneTitle,
                    R.string.atp_OnboardingLatsPageOneSubtitle),
                OnboardingPage(
                    R.drawable.logo_full,
                    R.string.atp_OnboardingLastPageTwoTitle,
                    R.string.atp_OnboardingLastPageTwoSubTitle),
                OnboardingPage(
                    R.drawable.logo_full,
                    R.string.atp_OnboardingLastPageThreeTitle,
                    R.string.atp_OnboardingLastPageThreeSubTitle))
    }
}
