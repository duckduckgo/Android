/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.privacy.dashboard.impl.ui

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.ui.AppPrivacyDashboardPayloadAdapter.BreakageReportRequest
import com.duckduckgo.privacy.dashboard.impl.ui.AppPrivacyDashboardPayloadAdapter.PrivacyProtectionsClicked
import com.duckduckgo.privacy.dashboard.impl.ui.AppPrivacyDashboardPayloadAdapter.ToggleReportOptions
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named

interface PrivacyDashboardPayloadAdapter {
    fun onUrlClicked(payload: String): String
    fun onOpenSettings(payload: String): String
    fun onSubmitBrokenSiteReport(payload: String): BreakageReportRequest?
    fun onPrivacyProtectionsClicked(payload: String): PrivacyProtectionsClicked?
    fun onGetToggleReportOptions(payload: ToggleReportOptions): String
}

@ContributesBinding(AppScope::class)
class AppPrivacyDashboardPayloadAdapter @Inject constructor(
    @Named("privacyDashboard") private val moshi: Moshi,
) : PrivacyDashboardPayloadAdapter {
    override fun onUrlClicked(payload: String): String {
        val payloadAdapter = moshi.adapter(Payload::class.java)
        return kotlin.runCatching { payloadAdapter.fromJson(payload)?.url ?: "" }.getOrDefault("")
    }

    override fun onOpenSettings(payload: String): String {
        val payloadAdapter = moshi.adapter(SettingsPayload::class.java)
        return kotlin.runCatching { payloadAdapter.fromJson(payload)?.target ?: "" }.getOrDefault("")
    }

    override fun onSubmitBrokenSiteReport(payload: String): BreakageReportRequest? {
        val payloadAdapter = moshi.adapter(BreakageReportRequest::class.java)
        return kotlin.runCatching { payloadAdapter.fromJson(payload) }.getOrNull()
    }

    override fun onGetToggleReportOptions(payload: ToggleReportOptions): String {
        val payloadAdapter = moshi.adapter(ToggleReportOptions::class.java)
        return kotlin.runCatching { payloadAdapter.toJson(payload) }.getOrDefault("")
    }
    override fun onPrivacyProtectionsClicked(payload: String): PrivacyProtectionsClicked? {
        val payloadAdapter = moshi.adapter(PrivacyProtectionsClicked::class.java)
        return kotlin.runCatching { payloadAdapter.fromJson(payload) }.getOrNull()
    }

    data class Payload(val url: String)
    data class SettingsPayload(val target: String)

    data class BreakageReportRequest(
        val category: String,
        val description: String,
    )

    data class PrivacyProtectionsClicked(
        val isProtected: Boolean,
        val eventOrigin: EventOrigin,
    )

    data class ToggleReportOptions(
        val data: List<ToggleReportOption>,
    ) {
        data class ToggleReportOption(
            val id: String,
            val additional: Additional? = null,
        )
        data class Additional(
            val url: String? = null,
        )
    }
}
