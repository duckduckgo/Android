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

package com.duckduckgo.app.browser.pageload

import android.net.TrafficStats
import android.os.Process
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface RxBytesProvider {
    /** Bytes received by this app's UID since boot, or [TrafficStats.UNSUPPORTED] when the device does not report it. */
    fun uidRxBytes(): Long
}

@ContributesBinding(AppScope::class)
class RealRxBytesProvider @Inject constructor() : RxBytesProvider {
    override fun uidRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid())
}
