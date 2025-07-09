/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.feedback

import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.DDG_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.SUBSCRIPTION_SETTINGS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.UNKNOWN
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_EXCLUDED_APPS
import com.duckduckgo.subscriptions.api.PrivacyProUnifiedFeedback.PrivacyProFeedbackSource.VPN_MANAGEMENT
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.DUCK_AI
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.ITR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.PIR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.SUBS_AND_PAYMENTS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackCategory.VPN
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackDuckAiSubCategory.ACCESS_SUBSCRIPTION_MODELS
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackDuckAiSubCategory.LOGIN_THIRD_PARTY_BROWSER
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackDuckAiSubCategory.OTHER
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackItrSubCategory.ACCESS_CODE_ISSUE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackItrSubCategory.CANT_CONTACT_ADVISOR
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackItrSubCategory.UNHELPFUL
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackPirSubCategory.INFO_NOT_ON_SPECIFIC_SITE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackPirSubCategory.RECORDS_NOT_ON_USER
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackPirSubCategory.REMOVAL_STUCK
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackPirSubCategory.SCAN_STUCK
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.GENERAL_FEEDBACK
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REPORT_PROBLEM
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackReportType.REQUEST_FEATURE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackSubsSubCategory.ONE_TIME_PASSWORD
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.BROWSER_CRASH_FREEZE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.CANNOT_CONNECT_TO_LOCAL_DEVICE
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.FAILS_TO_CONNECT
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.ISSUES_WITH_APPS_OR_WEBSITES
import com.duckduckgo.subscriptions.impl.feedback.SubscriptionFeedbackVpnSubCategory.SLOW_CONNECTION

enum class SubscriptionFeedbackReportType {
    REPORT_PROBLEM,
    REQUEST_FEATURE,
    GENERAL_FEEDBACK,
}

enum class SubscriptionFeedbackCategory {
    SUBS_AND_PAYMENTS,
    VPN,
    PIR,
    ITR,
    DUCK_AI,
}

interface SubscriptionFeedbackSubCategory
enum class SubscriptionFeedbackVpnSubCategory : SubscriptionFeedbackSubCategory {
    FAILS_TO_CONNECT,
    SLOW_CONNECTION,
    ISSUES_WITH_APPS_OR_WEBSITES,
    CANNOT_CONNECT_TO_LOCAL_DEVICE,
    BROWSER_CRASH_FREEZE,
    OTHER,
}

enum class SubscriptionFeedbackSubsSubCategory : SubscriptionFeedbackSubCategory {
    ONE_TIME_PASSWORD,
    OTHER,
}

enum class SubscriptionFeedbackPirSubCategory : SubscriptionFeedbackSubCategory {
    INFO_NOT_ON_SPECIFIC_SITE,
    RECORDS_NOT_ON_USER,
    SCAN_STUCK,
    REMOVAL_STUCK,
    OTHER,
}

enum class SubscriptionFeedbackItrSubCategory : SubscriptionFeedbackSubCategory {
    ACCESS_CODE_ISSUE,
    CANT_CONTACT_ADVISOR,
    UNHELPFUL,
    OTHER,
}

enum class SubscriptionFeedbackDuckAiSubCategory : SubscriptionFeedbackSubCategory {
    ACCESS_SUBSCRIPTION_MODELS,
    LOGIN_THIRD_PARTY_BROWSER,
    OTHER,
}

internal fun PrivacyProFeedbackSource.asParams(): String {
    return when (this) {
        DDG_SETTINGS -> "settings"
        SUBSCRIPTION_SETTINGS -> "ppro"
        VPN_MANAGEMENT -> "vpn"
        VPN_EXCLUDED_APPS -> "vpnExcludedApps"
        UNKNOWN -> "unknown"
    }
}

internal fun SubscriptionFeedbackReportType.asParams(): String {
    return when (this) {
        REPORT_PROBLEM -> "reportIssue"
        REQUEST_FEATURE -> "requestFeature"
        GENERAL_FEEDBACK -> "general"
    }
}

internal fun SubscriptionFeedbackCategory.asParams(): String {
    return when (this) {
        SUBS_AND_PAYMENTS -> "subscription"
        VPN -> "vpn"
        PIR -> "pir"
        ITR -> "itr"
        DUCK_AI -> "duckAi"
    }
}

internal fun SubscriptionFeedbackSubCategory.asParams(): String {
    return when (this) {
        is SubscriptionFeedbackVpnSubCategory -> this.asParams()
        is SubscriptionFeedbackSubsSubCategory -> this.asParams()
        is SubscriptionFeedbackPirSubCategory -> this.asParams()
        is SubscriptionFeedbackItrSubCategory -> this.asParams()
        is SubscriptionFeedbackDuckAiSubCategory -> this.asParams()
        else -> "unknown"
    }
}

internal fun SubscriptionFeedbackVpnSubCategory.asParams(): String {
    return when (this) {
        FAILS_TO_CONNECT -> "failsToConnect"
        SLOW_CONNECTION -> "tooSlow"
        ISSUES_WITH_APPS_OR_WEBSITES -> "issueWithAppOrWebsite"
        CANNOT_CONNECT_TO_LOCAL_DEVICE -> "cantConnectToLocalDevice"
        BROWSER_CRASH_FREEZE -> "appCrashesOrFreezes"
        SubscriptionFeedbackVpnSubCategory.OTHER -> "somethingElse"
    }
}

internal fun SubscriptionFeedbackSubsSubCategory.asParams(): String {
    return when (this) {
        ONE_TIME_PASSWORD -> "otp"
        SubscriptionFeedbackSubsSubCategory.OTHER -> "somethingElse"
    }
}

internal fun SubscriptionFeedbackPirSubCategory.asParams(): String {
    return when (this) {
        INFO_NOT_ON_SPECIFIC_SITE -> "nothingOnSpecificSite"
        RECORDS_NOT_ON_USER -> "notMe"
        SCAN_STUCK -> "scanStuck"
        REMOVAL_STUCK -> "removalStuck"
        SubscriptionFeedbackPirSubCategory.OTHER -> "somethingElse"
    }
}

internal fun SubscriptionFeedbackItrSubCategory.asParams(): String {
    return when (this) {
        ACCESS_CODE_ISSUE -> "accessCode"
        CANT_CONTACT_ADVISOR -> "cantContactAdvisor"
        UNHELPFUL -> "advisorUnhelpful"
        SubscriptionFeedbackItrSubCategory.OTHER -> "somethingElse"
    }
}

internal fun SubscriptionFeedbackDuckAiSubCategory.asParams(): String {
    return when (this) {
        ACCESS_SUBSCRIPTION_MODELS -> "accessSubscriptionModels"
        LOGIN_THIRD_PARTY_BROWSER -> "loginThirdPartyBrowser"
        OTHER -> "somethingElse"
    }
}
