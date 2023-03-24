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

package com.duckduckgo.autofill.impl.ui.credential.updating

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.FragmentScope
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class AutofillUpdatingExistingCredentialViewModel @Inject constructor() : ViewModel() {

    fun ellipsizeIfNecessary(username: String): String {
        return if (username.length > USERNAME_MAX_LENGTH) {
            username.take(USERNAME_MAX_LENGTH - 1) + Typography.ellipsis
        } else {
            username
        }
    }

    companion object {
        private const val USERNAME_MAX_LENGTH = 50
    }
}
