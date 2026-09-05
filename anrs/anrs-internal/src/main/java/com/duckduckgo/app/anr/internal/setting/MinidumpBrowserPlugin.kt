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
import android.content.Intent
import com.duckduckgo.app.anr.internal.feature.CrashAnrDevCapabilityPlugin
import com.duckduckgo.app.anr.internal.feature.MinidumpListActivity
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class MinidumpBrowserPlugin @Inject constructor(
    private val context: Context,
) : CrashAnrDevCapabilityPlugin {

    override fun title(): String = "Minidump Browser"

    override fun subtitle(): String {
        val crashpadDir = context.filesDir.resolve("crashpad")
        val counts = listOf("new", "pending", "completed").associateWith { status ->
            crashpadDir.resolve(status)
                .takeIf { it.isDirectory }
                ?.listFiles { f -> f.extension == "dmp" }
                ?.size ?: 0
        }
        val total = counts.values.sum()
        return if (total == 0) {
            "No minidumps"
        } else {
            "$total dump(s) — ${counts["new"]} new, ${counts["pending"]} pending, ${counts["completed"]} completed"
        }
    }

    override fun onCapabilityClicked(activityContext: Context) {
        activityContext.startActivity(Intent(activityContext, MinidumpListActivity::class.java))
    }
}
