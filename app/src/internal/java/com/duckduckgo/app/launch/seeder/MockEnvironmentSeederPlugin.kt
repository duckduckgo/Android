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

package com.duckduckgo.app.launch.seeder

import android.os.Process
import android.util.Log
import com.duckduckgo.app.mockenvironment.MockEnvironmentReadiness
import com.duckduckgo.app.mockenvironment.MockEnvironmentStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

/**
 * Enabled by test harnesses through the seeder extra mockEnvironment=<fixture port>: pins the tracker data and surrogates, proxies the
 * WebView to the fixture server on that port and logs mockEnvironment=seeded.
 *
 * Mocked:
 * - Tracker data (TDS) and surrogates, served from assets/mockenvironment.
 * - WebView traffic, proxied to the fixture server with no direct fallback.
 *
 * Not mocked yet (still reaches the real network):
 * - Privacy config and remote feature flags.
 * - Pixels.
 * - Other app API calls (remote messaging, subscriptions, autoconsent and scriptlet downloads, etc.).
 * - App-side loads outside WebView (favicons, page titles, crash reporting, Firebase).
 */
@ContributesIntoSet(AppScope::class)
class MockEnvironmentSeederPlugin @Inject constructor(
    private val store: MockEnvironmentStore,
    private val readiness: MockEnvironmentReadiness,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.MOCK_ENVIRONMENT.key)

    override suspend fun apply(key: String, value: String) {
        val port = value.toIntOrNull()?.takeIf { it in 1..65535 }
        if (port == null) {
            Log.e(TAG, "mockEnvironment=failed reason=invalid_port pid=${Process.myPid()}")
            return
        }
        store.fixturePort = port
        store.isEnabled = true
        readiness.seed()
    }

    private companion object {
        const val TAG = "DdgTestSeeder"
    }
}
