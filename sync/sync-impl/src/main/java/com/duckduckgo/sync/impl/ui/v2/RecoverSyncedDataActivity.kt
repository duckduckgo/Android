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
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeBucket
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeHandler
import com.duckduckgo.common.utils.edgetoedge.EdgeToEdgeProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.impl.databinding.ActivitySyncV2RecoverSyncedDataBinding
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import javax.inject.Inject

@InjectWith(ActivityScope::class)
class RecoverSyncedDataActivity : DuckDuckGoActivity() {
    private val binding by viewBinding<ActivitySyncV2RecoverSyncedDataBinding>()

    @Inject
    lateinit var edgeToEdgeProvider: EdgeToEdgeProvider

    @Inject
    lateinit var edgeToEdgeHandler: EdgeToEdgeHandler

    @Inject
    lateinit var syncPixels: SyncPixels

    private val launchSource get() = intent.getStringExtra(LAUNCH_SOURCE_EXTRA_KEY)

    private val readSyncCodeLauncher = registerForActivityResult(
        ReadSyncCodeContract(),
    ) { output ->
        when (output) {
            is ReadSyncCodeContract.Output.SyncCompleted -> {
                setResult(SyncPairingResult.RESULT_SYNC_COMPLETED, SyncPairingResult.resultIntent(output.result))
                finish()
            }

            is ReadSyncCodeContract.Output.Dismissed -> Unit
        }
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

        configureToolbar()
        configureRecoverDataCta()
    }

    private fun configureEdgeToEdgeInsets() {
        edgeToEdgeHandler.applyHorizontalSystemBarInsets(binding.root)
        edgeToEdgeHandler.applyStatusBarInsets(binding.toolbar)
        edgeToEdgeHandler.applyNavigationBarInsetsAsMargin(binding.recoverDataButton)
    }

    private fun configureToolbar() {
        binding.closeButton.setOnClickListener {
            finish()
        }
    }

    private fun configureRecoverDataCta() {
        binding.recoverDataButton.setOnClickListener {
            syncPixels.fireRecoverSyncDataConfirmed()
            readSyncCodeLauncher.launch(
                ReadSyncCodeContract.Input(
                    syncEntryPoint = SyncEntryPoint.RECOVER_SYNCED_DATA,
                    launchSource = launchSource,
                ),
            )
        }
    }

    companion object {
        private const val LAUNCH_SOURCE_EXTRA_KEY = "launch_source"

        fun intent(
            context: Context,
            launchSource: String?,
        ): Intent {
            return Intent(context, RecoverSyncedDataActivity::class.java).apply {
                putExtra(LAUNCH_SOURCE_EXTRA_KEY, launchSource)
            }
        }
    }
}
