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

package com.duckduckgo.espresso

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * Holds the test until the remote privacy config downloaded at startup has been applied.
 *
 * A fresh install starts on the bundled config. A page that starts loading before the remote config is applied gets
 * scripts built from the bundled config, or is reloaded mid-load when the remote config flips a feature, so the test
 * would no longer check what the remote config sets. Order this rule before the activity rule, so the test page only
 * starts loading once the remote config is in.
 */
class WaitForRemotePrivacyConfigRule(
    private val timeoutMillis: Long = TimeUnit.SECONDS.toMillis(60),
) : TestRule {

    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val workManager = WorkManager.getInstance(InstrumentationRegistry.getInstrumentation().targetContext)
            try {
                runBlocking {
                    withTimeout(timeoutMillis) {
                        workManager.getWorkInfosForUniqueWorkFlow(PRIVACY_CONFIG_DOWNLOAD_WORK_NAME)
                            .first { infos -> infos.firstOrNull()?.let { hasDownloaded(it) } == true }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError("Remote privacy config was not applied within ${timeoutMillis}ms", e)
            }
            base.evaluate()
        }
    }

    private fun hasDownloaded(info: WorkInfo): Boolean {
        // WorkManager counts an attempt when a run starts, so the count is only meaningful between runs.
        if (info.state != WorkInfo.State.ENQUEUED) return false
        // A successful periodic run resets the count; a run that asked for a retry keeps it.
        check(info.runAttemptCount == 0) { "Remote privacy config download failed, the worker is retrying" }
        // Before the first run the next run is due now; after a successful run it is a full period away.
        return info.nextScheduleTimeMillis - System.currentTimeMillis() > TimeUnit.MINUTES.toMillis(30)
    }

    private companion object {
        // Mirrors the unique work name in PrivacyConfigDownloadWorkerScheduler.
        const val PRIVACY_CONFIG_DOWNLOAD_WORK_NAME = "PRIVACY_CONFIG_DOWNLOADER_WORKER_TAG"
    }
}
