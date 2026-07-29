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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2AnotherDeviceBinding
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(ActivityScope::class)
class ExchangeSyncCodeActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2AnotherDeviceBinding>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    private val syncUrl
        get() = requireNotNull(intent.getStringExtra(SYNC_URL_EXTRA_KEY)) {
            "Missing intent extra: '$SYNC_URL_EXTRA_KEY'"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isEdgeToEdge = edgeToEdgeProvider.isEnabled(EdgeToEdgeBucket.SYNC)
        if (isEdgeToEdge) {
            enableTransparentEdgeToEdge()
        }
        setContentView(binding.root)
        if (isEdgeToEdge) {
            configureEdgeToEdgeInsets()
        }

        configureConnectingLabel()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applySystemBarInsets(binding.root)
    }

    private fun configureConnectingLabel() {
        val progressDrawableSpec = CircularProgressIndicatorSpec(this, null, 0).apply {
            indicatorSize = 20.toPx()
            indicatorInset = 0
            trackThickness = 3.toPx()
            indicatorColors = intArrayOf(getColorFromAttr(CommonR.attr.daxColorAccentBlue))
        }
        val progressDrawable = IndeterminateDrawable.createCircularDrawable(this, progressDrawableSpec).apply {
            setVisible(true, false)
        }
        binding.connectingLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(progressDrawable, null, null, null)
    }

    companion object {
        private const val SYNC_URL_EXTRA_KEY = "sync_url"
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"

        fun intent(
            context: Context,
            syncUrl: String,
            launchSource: String?,
        ): Intent {
            return Intent(context, ExchangeSyncCodeActivity::class.java).apply {
                putExtra(SYNC_URL_EXTRA_KEY, syncUrl)
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
