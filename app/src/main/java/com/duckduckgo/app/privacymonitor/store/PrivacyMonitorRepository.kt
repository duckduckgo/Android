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

package com.duckduckgo.app.privacymonitor.store


import android.arch.lifecycle.MutableLiveData
import com.duckduckgo.app.privacymonitor.PrivacyMonitor
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class PrivacyMonitorRepository @Inject constructor() {

    /**
     * Note: When we add tabs we will have multiple browsers and need multiple
     * version of this. We could swap this for a map of live data objects
     * using a guid as a key. The Browser/TabActivity could share the key
     * with the PrivacyDashboardActivity via the the intent bundle
     */
    val privacyMonitor: MutableLiveData<PrivacyMonitor> = MutableLiveData()

}
