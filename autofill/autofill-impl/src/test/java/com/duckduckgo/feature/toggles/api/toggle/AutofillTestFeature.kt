/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.feature.toggles.api.toggle

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.feature.toggles.api.Toggle

class AutofillTestFeature : AutofillFeature {
    var topLevelFeatureEnabled: Boolean = false
    var canInjectCredentials: Boolean = false
    var canSaveCredentials: Boolean = false
    var canGeneratePassword: Boolean = false
    var canAccessCredentialManagement: Boolean = false
    var onByDefault: Boolean = false

    override fun self(): Toggle = TestToggle(topLevelFeatureEnabled)
    override fun canInjectCredentials(): Toggle = TestToggle(canInjectCredentials)
    override fun canSaveCredentials(): Toggle = TestToggle(canSaveCredentials)
    override fun canGeneratePasswords(): Toggle = TestToggle(canGeneratePassword)
    override fun canAccessCredentialManagement(): Toggle = TestToggle(canAccessCredentialManagement)
    override fun onByDefault(): Toggle = TestToggle(onByDefault)
}

open class TestToggle(val enabled: Boolean) : Toggle {
    override fun getRawStoredState(): Toggle.State? = null
    override fun setEnabled(state: Toggle.State) {}
    override fun isEnabled(): Boolean = enabled
}
