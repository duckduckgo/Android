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

package com.duckduckgo.app.privacy.renderer

import android.content.Context
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.model.PrivacyGrade

class PrivacyUpgradeRenderer {

    fun heading(context: Context, before: PrivacyGrade, after: PrivacyGrade, privacyOn: Boolean): String {
        if (before != after) {
            return context.getString(R.string.privacyProtectionUpgraded, before.smallIcon(), after.smallIcon())
        }
        val resource = if (privacyOn) R.string.privacyProtectionEnabled else R.string.privacyProtectionDisabled
        return context.getString(resource)
    }
}
