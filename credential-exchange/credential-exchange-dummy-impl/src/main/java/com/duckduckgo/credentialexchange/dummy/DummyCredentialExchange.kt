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

package com.duckduckgo.credentialexchange.dummy

import com.duckduckgo.credentialexchange.api.CredentialExchange
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure.NOT_SUPPORTED
import com.duckduckgo.credentialexchange.api.CredentialExchangeLauncher
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult.Failure
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/**
 * Used in builds where credential transfer backend is not supported, like F-Droid.
 * The flow cannot run at all there, so it is always reported as unsupported.
 */
@ContributesBinding(AppScope::class)
class DummyCredentialExchange @Inject constructor() : CredentialExchange {

    override suspend fun isImportSupported(): Boolean = false
}

@ContributesBinding(ActivityScope::class)
class DummyCredentialExchangeLauncher @Inject constructor() : CredentialExchangeLauncher {

    override suspend fun launchImportFlow(): CredentialExchangeResult = Failure(NOT_SUPPORTED)
}
