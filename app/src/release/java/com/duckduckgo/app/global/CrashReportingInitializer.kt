/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global

import android.app.Application
import com.duckduckgo.app.browser.R
import org.acra.ACRA
import org.acra.ReportField
import org.acra.ReportingInteractionMode
import org.acra.config.ConfigurationBuilder
import timber.log.Timber
import javax.inject.Inject

class CrashReportingInitializer @Inject constructor(){

    fun init(application: Application) {
        val config = ConfigurationBuilder(application)
                .setFormUri("https://duckduckgo.com/crash.js")
                .setReportingInteractionMode(ReportingInteractionMode.DIALOG)
                .setCustomReportContent(*reportContent())
                .setResToastText(R.string.crash_toast_text)
                .setResDialogText(R.string.crash_dialog_text)
                .setResDialogCommentPrompt(R.string.crash_dialog_comment_prompt)
                .setResDialogOkToast(R.string.crash_dialog_ok_toast)
                .build()

        ACRA.init(application, config)

        Timber.i("Crash reporting configured")
    }

    private fun reportContent(): Array<ReportField> =
            arrayOf(ReportField.APP_VERSION_CODE, ReportField.APP_VERSION_NAME, ReportField.ANDROID_VERSION, ReportField.STACK_TRACE, ReportField.AVAILABLE_MEM_SIZE, ReportField.USER_COMMENT, ReportField.LOGCAT, ReportField.PRODUCT, ReportField.PHONE_MODEL)
}