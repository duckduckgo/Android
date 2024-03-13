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

package com.duckduckgo.networkprotection.impl.about

import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenAndEnable
import com.duckduckgo.networkprotection.impl.about.NetworkProtectionAboutScreens.VpnTermsScreenNoParams
import com.duckduckgo.networkprotection.impl.databinding.ActivityVpnTermsBinding
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistCodeNotification
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import javax.inject.Inject

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(VpnTermsScreenNoParams::class)
class VpnTermsActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var pixels: NetworkProtectionPixels

    @Inject
    lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Inject
    lateinit var waitlistNotification: NetPWaitlistCodeNotification

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val binding: ActivityVpnTermsBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        pixels.reportTermsShown()

        binding.termsAgreeAndClose.setOnClickListener {
            pixels.reportTermsAccepted()
            netPWaitlistRepository.acceptWaitlistTerms()
            waitlistNotification.cancelNotification()
            openAndEnableNetP()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun openAndEnableNetP() {
        globalActivityStarter.start(this, NetworkProtectionManagementScreenAndEnable(true))
        finish()
    }
}
