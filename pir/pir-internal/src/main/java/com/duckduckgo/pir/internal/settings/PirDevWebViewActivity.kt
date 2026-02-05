/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.pir.impl.email.PirEmailConfirmation
import com.duckduckgo.pir.impl.optout.PirOptOut
import com.duckduckgo.pir.impl.scan.PirScan
import com.duckduckgo.pir.impl.store.PirSchedulingRepository
import com.duckduckgo.pir.internal.databinding.ActivityPirInternalWebviewBinding
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(PirDevWebViewScreenParams::class)
class PirDevWebViewActivity : DuckDuckGoActivity() {
    @Inject
    lateinit var pirOptOut: PirOptOut

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var pirScan: PirScan

    @Inject
    lateinit var pirEmailConfirmation: PirEmailConfirmation

    @Inject
    lateinit var pirSchedulingRepository: PirSchedulingRepository

    private val binding: ActivityPirInternalWebviewBinding by viewBinding()
    private val params: PirDevWebViewScreenParams?
        get() = intent.getActivityParams(PirDevWebViewScreenParams::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        when (val screenParams = params) {
            is PirDevWebViewScreenParams.PirDevEmailWebViewScreenParams -> runDebugEmail(screenParams)
            is PirDevWebViewScreenParams.PirDevOptOutWebViewScreenParams -> runDebugOptOut(screenParams)
            is PirDevWebViewScreenParams.PirDevScanWebViewScreenParams -> runDebugScan(screenParams)
            null -> finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        when (params) {
            is PirDevWebViewScreenParams.PirDevOptOutWebViewScreenParams -> pirOptOut.stop()
            is PirDevWebViewScreenParams.PirDevScanWebViewScreenParams -> pirScan.stop()
            is PirDevWebViewScreenParams.PirDevEmailWebViewScreenParams -> pirEmailConfirmation.stop()
            null -> {}
        }
    }

    private fun runDebugScan(params: PirDevWebViewScreenParams.PirDevScanWebViewScreenParams) {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            logcat { "PIR-DEV: Debug SCAN started at $startTime" }
            pirScan.debugExecute(params.brokers, binding.pirDevWebView).also {
                val duration = (System.currentTimeMillis() - startTime).milliseconds
                logcat { "PIR-DEV: Debug SCAN finished in ${duration.inWholeSeconds} seconds / ${duration.inWholeMinutes} minutes" }
                finish()
            }
        }
    }

    private fun runDebugOptOut(params: PirDevWebViewScreenParams.PirDevOptOutWebViewScreenParams) {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            logcat { "PIR-DEV: Debug OPT_OUT started at $startTime" }
            pirOptOut.debugExecute(params.brokers, binding.pirDevWebView).also {
                val duration = (System.currentTimeMillis() - startTime).milliseconds
                logcat { "PIR-DEV: Debug OPT_OUT finished in ${duration.inWholeSeconds} seconds / ${duration.inWholeMinutes} minutes" }
                finish()
            }
        }
    }

    private fun runDebugEmail(params: PirDevWebViewScreenParams.PirDevEmailWebViewScreenParams) {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            logcat { "PIR-DEV: Debug EMAIL_CONFIRMATION started at $startTime" }
            val jobRecord = pirSchedulingRepository.getEmailConfirmationJob(params.extractedProfileId)
            if (jobRecord != null) {
                pirEmailConfirmation.debugExecute(jobRecord, binding.pirDevWebView).also {
                    val duration = (System.currentTimeMillis() - startTime).milliseconds
                    logcat { "PIR-DEV: Debug EMAIL_CONFIRMATION finished in ${duration.inWholeSeconds} seconds / ${duration.inWholeMinutes} minutes" }
                    finish()
                }
            } else {
                logcat { "PIR-DEV: No email confirmation job found for extractedProfileId ${params.extractedProfileId}" }
                finish()
            }
        }
    }
}

sealed interface PirDevWebViewScreenParams : ActivityParams {

    data class PirDevScanWebViewScreenParams(
        val brokers: List<String>,
    ) : PirDevWebViewScreenParams

    data class PirDevOptOutWebViewScreenParams(
        val brokers: List<String>,
    ) : PirDevWebViewScreenParams

    data class PirDevEmailWebViewScreenParams(
        val extractedProfileId: Long,
    ) : PirDevWebViewScreenParams
}
