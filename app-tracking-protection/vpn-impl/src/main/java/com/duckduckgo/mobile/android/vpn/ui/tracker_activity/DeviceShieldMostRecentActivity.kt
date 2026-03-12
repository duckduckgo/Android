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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.edgetoedge.api.EdgeToEdge
import com.duckduckgo.mobile.android.vpn.databinding.ActivityDeviceShieldAllTracerActivityBinding
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class DeviceShieldMostRecentActivity : DuckDuckGoActivity() {

    @Inject lateinit var edgeToEdge: EdgeToEdge

    private val binding: ActivityDeviceShieldAllTracerActivityBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        edgeToEdge.enableIfToggled(this)
        setContentView(binding.root)
        setupToolbar(binding.includeToolbar.toolbar)
        setupEdgeToEdge()
    }

    private fun setupEdgeToEdge() {
        if (!edgeToEdge.isEnabled()) return
        ViewCompat.setOnApplyWindowInsetsListener(binding.activityList) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        internal fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldMostRecentActivity::class.java)
        }
    }
}
