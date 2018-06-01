/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.feedback.api

import android.annotation.SuppressLint
import android.os.Build
import com.duckduckgo.app.feedback.api.FeedbackService.Platform
import com.duckduckgo.app.feedback.api.FeedbackService.Reason
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

interface FeedbackSender {
    fun submitGeneralFeedback(comment: String)
    fun submitBrokenSiteFeedback(comment: String, url: String)
}

class FeedbackSubmitter(
    private val statisticsStore: StatisticsDataStore,
    private val variantManager: VariantManager,
    private val service: FeedbackService
) : FeedbackSender {

    @SuppressLint("CheckResult")
    override fun submitGeneralFeedback(comment: String) {
        submitFeedback(Reason.GENERAL, comment, "")
    }

    override fun submitBrokenSiteFeedback(comment: String, url: String) {
        submitFeedback(Reason.BROKEN_SITE, comment, url)
    }

    private fun submitFeedback(type: String, comment: String, url: String) {
        service.feedback(
            type,
            url,
            comment,
            Platform.ANDROID,
            Build.VERSION.SDK_INT,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.RELEASE,
            atbWithVariant()
        )
            .subscribeOn(Schedulers.io())
            .subscribe({
                Timber.v("Feedback submission succeeded")
            }, {
                Timber.w("Feedback submission failed ${it.localizedMessage}")
            })
    }

    private fun atbWithVariant(): String {
        return statisticsStore.atb?.formatWithVariant(variantManager.getVariant()) ?: ""
    }
}