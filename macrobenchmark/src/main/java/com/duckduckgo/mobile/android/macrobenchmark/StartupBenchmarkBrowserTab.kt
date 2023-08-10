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

package com.duckduckgo.mobile.android.macrobenchmark

import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Benchmark for Browser Tab Startup. Run this benchmark on a physical device
 * from Android Studio to see startup measurements, and system trace recordings
 * for investigating app's performance.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarkBrowserTab {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupBrowserTab() = startupBenchmark(
        setupBlock = {
            launchBrowserTab()
            pressHome()
        }
    )

    @Test
    fun startupBrowserTabAndLoadDuckDuckGo() = startupBenchmark(
        setupBlock = {
            launchBrowserTab()
            pressHome()
        },
        measureBlock = {
            startActivityAndWait()

            val selector = UiSelector()
                .className("android.widget.EditText")
                .instance(0)
            // [ANA] TEMP: Tested the below 3 websites in individual benchmark runs.
            device.findObject(selector).text = "https://www.duckduckgo.com"
//            device.findObject(selector).text = "https://www.wikipedia.org"
//            device.findObject(selector).text = "https://www.bbc.com"
            device.pressEnter()

            device.waitForIdle(TIMEOUT_MS)
        }
    )

    private fun MacrobenchmarkScope.launchBrowserTab() {
        startActivityAndWait()

        // Handle Notifications permissions on Android 13 and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.wait(Until.hasObject(By.text("Allow DuckDuckGo to send you notifications?")), TIMEOUT_MS)
            val allowButton = device.findObject(By.text("Allow"))
            allowButton?.run {
                click()
            }
        }

        device.wait(Until.hasObject(By.text("Let's Do it!")), TIMEOUT_MS)
        val daxPrimaryCtaButton = device.findObject(By.res(packageName, "primaryCta"))
        daxPrimaryCtaButton?.run {
            click()
            device.wait(Until.hasObject(By.text("Cancel")), TIMEOUT_MS)
            val cancelButton = device.findObject(By.text("Cancel"))
            cancelButton?.click()
        }

        device.wait(Until.hasObject(By.text("Next,")), TIMEOUT_MS)
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun startupBenchmark(
        startupMode: StartupMode = StartupMode.WARM,
        setupBlock: MacrobenchmarkScope.() -> Unit,
        measureBlock: MacrobenchmarkScope.() -> Unit = { startActivityAndWait() },
    ) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE_NAME,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            TraceSectionMetric("LOAD_PAGE_START_TO_FINISH"),
            TraceSectionMetric("LOAD_PAGE_ON_PAGE_STARTED"),
            TraceSectionMetric("LOAD_PAGE_ON_PAGE_FINISHED"),
            TraceSectionMetric("LOAD_PAGE_SHOULD_OVERRIDE_URL_LOADING"),
            TraceSectionMetric("LOAD_PAGE_SHOULD_INTERCEPT_REQUEST"),
            TraceSectionMetric("LOAD_PAGE_ON_RENDER_PROCESS_GONE"),
            TraceSectionMetric("LOAD_PAGE_ON_RENDER_PROCESS_GONE"),
            TraceSectionMetric("LOAD_PAGE_ON_RECEIVED_SSL_ERROR"),
            TraceSectionMetric("LOAD_PAGE_ON_RECEIVED_SSL_ERROR"),
            TraceSectionMetric("TRACKER_DETECTOR_EVALUATE"),
            TraceSectionMetric("CLOAKED_CNAME_DETECTOR_DETECT"),
            TraceSectionMetric("DOM_LOGIN_DETECTOR_LOGIN_FORM_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("DOM_LOGIN_DETECTOR_LOGIN_FORM_EVENTS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("BLOB_CONVERTER_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("DOM_URL_EXTRACTOR_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("EMAIL_INJECTOR_INJECT_ADDRESS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("EMAIL_INJECTOR_NOTIFY_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("AUTOCONSENT_INJECT_AUTOCONSENT_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("AUTOCONSENT_OPT_OUT_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("EVAL_MESSAGE_HANDLER_PROCESS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("INIT_MESSAGE_HANDLER_PROCESS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("OPT_OUT_AUTOCONSENT_MESSAGE_HANDLER_PROCESS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("INLINE_BROWSER_AUTOFILL_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("REAL_MESSAGING_CSS_INJECT_CSS_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("REAL_MESSAGING_CSS_SEND_MESSAGE_EVALUATE_JAVASCRIPT"),
            TraceSectionMetric("PRIVACY_DASHBOARD_RENDER_EVALUATE_JAVASCRIPT"),
        ),
        iterations = ITERATIONS,
        startupMode = startupMode,
        setupBlock = setupBlock,
        measureBlock = measureBlock
    )
}
