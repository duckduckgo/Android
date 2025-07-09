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

package com.duckduckgo.autofill.impl.email.remoteconfig

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.autofill.impl.email.incontext.EmailProtectionInContextSignupFeature
import com.duckduckgo.di.scopes.AppScope

@ContributesRemoteFeature(
    scope = AppScope::class,
    boundType = EmailProtectionInContextSignupFeature::class,
    featureName = "incontextSignup",
    settingsStore = EmailProtectionInContextRemoteSettingsPersister::class,
)
@Suppress("unused")
private interface UnusedEmailProtectionInContextSignupRemoteFeatureCodegenTrigger
