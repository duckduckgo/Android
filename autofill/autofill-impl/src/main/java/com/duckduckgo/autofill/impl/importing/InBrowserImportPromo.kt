/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface InBrowserImportPromo {
    suspend fun canShowPromo(): Boolean
}

@ContributesBinding(AppScope::class)
class RealInBrowserImportPromo @Inject constructor(
    private val autofillStore: InternalAutofillStore,
): InBrowserImportPromo {

    override suspend fun canShowPromo(): Boolean {
        // TODO: stopShowing if:
        //  user has imported
        //  has more than 25 login
        //  more than 5 times shown
        //  user dismissed
        //  once per form
        return autofillStore.hasEverImportedPasswords.not()
    }
}
