/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.referral.impl

/** Persisted Play Store install-referrer attribution data. */
interface AppReferrerDataStore {
    /** Whether the install referrer has already been retrieved, so it is not fetched again. */
    var referrerCheckedPreviously: Boolean

    /** Two-character campaign code extracted from the referrer, if any. */
    var campaignSuffix: String?

    var installedFromEuAuction: Boolean

    /** Value of the referrer `origin` attribution parameter, if any. */
    var utmOriginAttributeCampaign: String?
}
