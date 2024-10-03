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

package com.duckduckgo.autofill.impl.email.incontext

import android.os.Bundle
import androidx.fragment.app.commit
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpHandleVerificationLink
import com.duckduckgo.autofill.api.EmailProtectionInContextSignUpStartScreen
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ActivityEmailProtectionInContextSignupBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(EmailProtectionInContextSignUpStartScreen::class)
@ContributeToActivityStarter(EmailProtectionInContextSignUpHandleVerificationLink::class)
class EmailProtectionInContextSignupActivity : DuckDuckGoActivity() {

    val binding: ActivityEmailProtectionInContextSignupBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportFragmentManager.commit {
            replace(R.id.fragment_container, EmailProtectionInContextSignupFragment())
        }
    }
}
