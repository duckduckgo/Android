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
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.duckduckgo.app.anr.internal.feature.CrashAnrDevCapabilityPlugin
import com.duckduckgo.di.scopes.AppScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class CrashpadUploadPlugin @Inject constructor(
    private val uploadConfig: CrashpadUploadConfig,
) : CrashAnrDevCapabilityPlugin {

    override fun title(): String = "Crashpad Upload"

    override fun subtitle(): String {
        val url = uploadConfig.uploadUrl
        return if (url.isEmpty()) "Uploads disabled" else url
    }

    override fun onCapabilityClicked(activityContext: Context) {
        val dp16 = (16 * activityContext.resources.displayMetrics.density).toInt()

        val layout = LinearLayout(activityContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp16, dp16, 0)
        }
        val urlInput = EditText(activityContext).apply {
            hint = "http://192.168.x.x:8080/upload"
            setText(uploadConfig.uploadUrl)
            isSingleLine = true
        }
        val noRateLimitCheck = CheckBox(activityContext).apply {
            text = "No rate limit"
            isChecked = uploadConfig.noRateLimit
        }
        layout.addView(urlInput)
        layout.addView(noRateLimitCheck)

        MaterialAlertDialogBuilder(activityContext)
            .setTitle("Crashpad Upload")
            .setView(layout)
            .setPositiveButton("Save & Restart") { _, _ ->
                uploadConfig.uploadUrl = urlInput.text.toString().trim()
                uploadConfig.noRateLimit = noRateLimitCheck.isChecked
                Handler(Looper.getMainLooper()).postDelayed(
                    { Process.killProcess(Process.myPid()) },
                    300,
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
