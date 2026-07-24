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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2CodeExchangeBinding
import com.google.android.material.tabs.TabLayoutMediator
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class CodeExchangeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2CodeExchangeBinding>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val edgeToEdgeEnabled = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (edgeToEdgeEnabled) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (edgeToEdgeEnabled) {
            configureEdgeToEdgeInsets()
        }

        configureToolbar()
        configureContentAdapter()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsets(binding.contentPager, drawBehindGestureNav = false)
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            finish()
        }
        binding.showQrCodeButton.setOnClickListener {
            startActivity(Intent(this, QrCodeActivity::class.java))
        }
    }

    private fun configureContentAdapter() {
        binding.contentPager.isUserInputEnabled = false
        binding.contentPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    SCANNER_POSITION -> CameraScannerFragment()
                    MANUAL_CODE_ENTRY_POSITION -> ManualCodeEntryFragment()
                    else -> error("Unknown position: $position")
                }
            }
        }
        val mediator = TabLayoutMediator(binding.tabContainer, binding.contentPager) { tab, position ->
            tab.text = when (position) {
                SCANNER_POSITION -> getString(R.string.sync_scanner_v2_scan_qr_code_tab_item_label)
                MANUAL_CODE_ENTRY_POSITION -> getString(R.string.sync_scanner_v2_enter_code_manually_tab_item_label)
                else -> error("Unknown position: $position")
            }
        }
        mediator.attach()
    }

    companion object {
        private const val SCANNER_POSITION = 0
        private const val MANUAL_CODE_ENTRY_POSITION = 1
    }
}
