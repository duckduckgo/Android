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

package com.duckduckgo.app.anr.internal.setting

import android.content.Context
import com.duckduckgo.android_crashkit.Crashpad
import com.duckduckgo.app.anr.internal.feature.CrashAnrDevCapabilityPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NativeCrashDumpPlugin @Inject constructor() : CrashAnrDevCapabilityPlugin {
    override fun title(): String = "Dump without crash"
    override fun subtitle(): String = "Capture a Crashpad minidump without terminating"
    override fun onCapabilityClicked(activityContext: Context) {
        Crashpad.dumpWithoutCrash()
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NativeCrashTriggerPlugin @Inject constructor() : CrashAnrDevCapabilityPlugin {
    override fun title(): String = "Trigger native crash"
    override fun subtitle(): String = "Force a native crash via Crashpad (app will terminate)"
    override fun onCapabilityClicked(activityContext: Context) {
        Crashpad.crash()
    }
}

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NativeCrashUploadPlugin @Inject constructor() : CrashAnrDevCapabilityPlugin {
    override fun title(): String = "Request upload for pending reports"
    override fun subtitle(): String = "Marks all pending minidumps for immediate upload by the handler"
    override fun onCapabilityClicked(activityContext: Context) {
        val count = Crashpad.requestUploadForPendingReports()
        android.widget.Toast.makeText(
            activityContext,
            "Requested upload for $count report(s)",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}
