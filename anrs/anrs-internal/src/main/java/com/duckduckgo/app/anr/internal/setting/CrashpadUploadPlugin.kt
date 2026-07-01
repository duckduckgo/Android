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
import android.view.LayoutInflater
import com.duckduckgo.app.anr.internal.R
import com.duckduckgo.app.anr.internal.databinding.DialogCrashpadUploadBinding
import com.duckduckgo.app.anr.internal.feature.CrashAnrDevCapabilityPlugin
import com.duckduckgo.common.ui.view.dialog.CustomAlertDialogBuilder
import com.duckduckgo.di.scopes.AppScope
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
        val binding = DialogCrashpadUploadBinding.inflate(LayoutInflater.from(activityContext))
        binding.uploadUrlInput.text = uploadConfig.uploadUrl
        binding.noRateLimitCheck.isChecked = uploadConfig.noRateLimit

        CustomAlertDialogBuilder(activityContext)
            .setTitle(R.string.crashpad_upload_title)
            .setPositiveButton(R.string.crashpad_save_and_restart)
            .setNegativeButton(R.string.crashpad_cancel)
            .setView(binding)
            .addEventListener(
                object : CustomAlertDialogBuilder.EventListener() {
                    override fun onPositiveButtonClicked() {
                        uploadConfig.uploadUrl = binding.uploadUrlInput.text.trim()
                        uploadConfig.noRateLimit = binding.noRateLimitCheck.isChecked
                        Handler(Looper.getMainLooper()).postDelayed(
                            { Process.killProcess(Process.myPid()) },
                            300,
                        )
                    }
                },
            )
            .show()
    }
}
